package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.SearchData;
import searchengine.dto.SearchResponse;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SearchIndex;
import searchengine.model.SiteEntity;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.SiteRepository;
import searchengine.utils.SnippetGenerator;
import searchengine.utils.Lemmatization;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchingServiceImpl implements SearchingService {
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final Lemmatization lemmatization;
    private final SnippetGenerator snippetGenerator;
    private final SitesList sitesList;

    @Override
    public SearchResponse getSearchResults(String query, String siteUrl, Integer offset, Integer limit) {
        long start = System.currentTimeMillis();
        System.out.println("Начало поиска: " + start);
        if (query.isEmpty()) {
            return new SearchResponse(false,
                    "Задан пустой поисковый запрос",
                    0,
                    new ArrayList<>());
        }

        Set<String> lemmasFromQuery = generateLemmasFromQuery(query);

        LinkedHashMap<String, Integer> lemmasSortedByFrequency = sortLemmasByFrequency(lemmasFromQuery);

        SearchResponse generatedSearchDataList;

        LinkedHashMap<LemmaEntity, PageEntity> entitiesList = new LinkedHashMap<>();

        if (siteUrl != null) {
            SiteEntity siteEntity = getSiteEntity(siteUrl);
            entitiesList = getEntitiesList(lemmasFromQuery, siteEntity, lemmasSortedByFrequency);

            LinkedHashMap<PageEntity, Integer> pagesByRelevance = countAbsoluteRank(entitiesList);
            LinkedHashMap<PageEntity, Integer> sortedPages = sortPagesByRelevance(pagesByRelevance);
            generatedSearchDataList = generateSearchDataList(sortedPages, lemmasFromQuery, limit, offset);

        } else {
            for (Site site : sitesList.getSites()) {
                System.out.println(">>> Поиск на сайте: " + site.getName());
                SiteEntity siteEntity = getSiteEntity(site.getUrl());
                entitiesList.putAll(getEntitiesList(lemmasFromQuery, siteEntity, lemmasSortedByFrequency));
            }
            LinkedHashMap<PageEntity, Integer> pagesByRelevance = countAbsoluteRank(entitiesList);
            LinkedHashMap<PageEntity, Integer> sortedPages = sortPagesByRelevance(pagesByRelevance);
            generatedSearchDataList = generateSearchDataList(sortedPages, lemmasFromQuery, limit, offset);
        }
        System.out.println("Окончание поиска: " + (System.currentTimeMillis() - start));
        return generatedSearchDataList;
    }

    private LinkedHashMap<LemmaEntity, PageEntity> getEntitiesList(Set<String> lemmasFromQuery, SiteEntity site,
                                                                   LinkedHashMap<String, Integer> lemmasSortedByFrequency) {
        List<PageEntity> pagesListFromFirstLemma = getPageEntityListFromFirstLemma(lemmasSortedByFrequency, site);
        List<PageEntity> pagesFilteredByNextLemmas = filterPagesByOtherLemmas(lemmasSortedByFrequency,
                pagesListFromFirstLemma);
        return compareFinalPagesAndLemmas(pagesFilteredByNextLemmas, lemmasFromQuery);
    }

    private SearchData generateSearchData(String site, String siteName, String uri,
                                          String title, String snippet, float relevance) {
        return SearchData.builder().site(site).siteName(siteName).uri(uri).title(title)
                .snippet(snippet).relevance(relevance).build();
    }

    private SearchResponse generateSearchDataList(LinkedHashMap<PageEntity, Integer> sortedPages,
                                                  Set<String> lemmasFromQuery, int limit, int offset) {

        if (offset != 0 || !sortedPages.isEmpty()) {
            sortedPages.remove(sortedPages.keySet().stream().findFirst().orElseThrow());
        }

        List<SearchData> dataList = new ArrayList<>();
        List<SearchData> newDataList;
        for (Map.Entry<PageEntity, Integer> entry : sortedPages.entrySet()) {
            dataList.add(generateSearchData(
                    entry.getKey().getSiteID().getUrl(),
                    entry.getKey().getSiteID().getName(),
                    shortThePath(entry.getKey(), entry.getKey().getSiteID()),
                    Jsoup.parse(entry.getKey().getContent()).title(),
                    getSnippet(entry.getKey(), lemmasFromQuery),
                    entry.getValue())
            );
        }

        if (dataList.size() < limit) {
            newDataList = dataList;
        } else if (dataList.size() - offset < limit){
            newDataList = dataList.subList(offset, dataList.size());
        } else {
            newDataList = dataList.subList(offset, offset + limit);
        }

        return new SearchResponse(true, dataList.size(), newDataList);
    }

    private String shortThePath(PageEntity page, SiteEntity site) {
        String pageURL = page.getPath();
        String siteURL = site.getUrl();
        return pageURL.replaceAll(siteURL, "");
    }

    private SiteEntity getSiteEntity(String siteURL) {
        return siteRepository.findSiteEntityByUrl(siteURL);
    }

    private String getSnippet(PageEntity page, Set<String> lemmas) {
        List<String> queryList = new ArrayList<>(lemmas);
        snippetGenerator.setText(page.getContent());
        snippetGenerator.setQueryWords(queryList);
        return snippetGenerator.generateSnippets();
    }

    private Set<String> generateLemmasFromQuery(String query) {
        return lemmatization.getLemmas(query).keySet();
    }

    private LinkedHashMap<String, Integer> sortLemmasByFrequency(Set<String> lemmasList) {
        LinkedHashMap<String, Integer> foundLemmas = new LinkedHashMap<>();
        for (String lemmaFromList : lemmasList) {
            AtomicInteger frequency = new AtomicInteger();
            List<LemmaEntity> lemmas = lemmaRepository.findLemmaEntitiesByLemma(lemmaFromList);
            lemmas = removeMostFrequentlyLemmas(lemmas);
            lemmas.forEach(lemmaEntity -> frequency.set(frequency.get() + lemmaEntity.getFrequency()));
            foundLemmas.put(lemmaFromList, frequency.intValue());
        }

        return foundLemmas.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry::getValue))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> {
                            throw new AssertionError();
                        },
                        LinkedHashMap::new));
    }

    private ArrayList<LemmaEntity> removeMostFrequentlyLemmas(List<LemmaEntity> lemmas) {
        ArrayList<LemmaEntity> reList = new ArrayList<>(lemmas);
        int removableLemmasPercent = 5;
        int removeCount = Math.round((float) lemmas.size() / 100 * removableLemmasPercent);
        LemmaEntity removable = new LemmaEntity();
        for (int i = 0; i < removeCount; i++) {
            int maxFrequency = 0;
            for (LemmaEntity lemma : lemmas) {
                if (lemma.getFrequency() > maxFrequency) {
                    maxFrequency = lemma.getFrequency();
                    removable = lemma;
                }
            }
            reList.remove(removable);
        }
        return reList;
    }

    private List<PageEntity> getPageEntityListFromFirstLemma(LinkedHashMap<String, Integer> sortedLemmas, SiteEntity site) {
        List<PageEntity> listFromFirstLemma = new ArrayList<>();
        ArrayList<String> lemmaList = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : sortedLemmas.entrySet()) {
            lemmaList.add(entry.getKey());
        }
        String rareLemma = lemmaList.get(0);
        ArrayList<SearchIndex> indexesFromFirstLemma = indexRepository.findByLemmaID_LemmaAndPageID_SiteID(rareLemma, site);
        indexesFromFirstLemma.forEach(searchIndex -> listFromFirstLemma.add(searchIndex.getPageID()));
        return listFromFirstLemma;
    }

    private List<PageEntity> filterPagesByOtherLemmas(LinkedHashMap<String, Integer> sortedLemmas,
                                                      List<PageEntity> pagesListFromFirstLemma) {
        List<PageEntity> refactoredList = new ArrayList<>(pagesListFromFirstLemma);
        ArrayList<String> lemmaList = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : sortedLemmas.entrySet()) {
            lemmaList.add(entry.getKey());
        }
        if (!lemmaList.isEmpty()) {
            lemmaList.remove(0);
            for (PageEntity page : pagesListFromFirstLemma) {
                for (String lemma : lemmaList) {
                    if (indexRepository.findByPageIDAndLemmaID_Lemma(page, lemma).isEmpty()) {
                        refactoredList.remove(page);
                    }
                }
            }
        }
        return refactoredList;
    }

    private LinkedHashMap<PageEntity, Integer> countAbsoluteRank(LinkedHashMap<LemmaEntity, PageEntity> lemmaAndPageList) {
        LinkedHashMap<PageEntity, Integer> sortedList = new LinkedHashMap<>();
        for (Map.Entry<LemmaEntity, PageEntity> entry : lemmaAndPageList.entrySet()) {
            if (sortedList.containsKey(entry.getValue())) {
                int rank = sortedList.get(entry.getValue());
                sortedList.remove(entry.getValue());
                sortedList.put(entry.getValue(), (entry.getKey().getFrequency() + rank));
            } else {
                sortedList.put(entry.getValue(), entry.getKey().getFrequency());
            }
        }
        return sortedList;
    }

    private LinkedHashMap<LemmaEntity, PageEntity> compareFinalPagesAndLemmas(List<PageEntity> pagesFilteredByNextLemmas,
                                                                              Set<String> lemmasFromQuery) {
        LinkedHashMap<LemmaEntity, PageEntity> finalPagesAndLemmasList = new LinkedHashMap<>();
        for (PageEntity page : pagesFilteredByNextLemmas) {
            for (String lemma : lemmasFromQuery) {
                indexRepository.findByPageIDAndLemmaID_Lemma(page, lemma)
                        .forEach(searchIndex ->
                                finalPagesAndLemmasList.put(searchIndex.getLemmaID(), searchIndex.getPageID()));
            }
        }
        return finalPagesAndLemmasList;
    }

    private LinkedHashMap<PageEntity, Integer> sortPagesByRelevance(LinkedHashMap<PageEntity, Integer> finalPages) {
        LinkedHashMap<PageEntity, Integer> sortedList;
        sortedList = finalPages.entrySet().stream().sorted(Comparator.comparingInt(e -> -e.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> {
                            throw new AssertionError();
                        },
                        LinkedHashMap::new
                ));
        return sortedList;
    }
}