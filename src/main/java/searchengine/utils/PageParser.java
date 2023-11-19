package searchengine.utils;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import searchengine.model.*;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class PageParser {
    private final SiteEntity site;
    private PageEntity page;
    private final String url;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private boolean contains;

    public void parsePage() {

        for (PageEntity page : pageRepository.findAll()) {
            if (page.getPath().equalsIgnoreCase(url)) {
                this.page = page;
                contains = true;
                break;
            }
        }
        if (contains) {
            List<SearchIndex> indexList = new ArrayList<>();
            for (SearchIndex searchIndex : indexRepository.findAll()) {
                if (searchIndex.getPageID().getId() == page.getId()) {
                    indexList.add(searchIndex);
                }
            }
            indexRepository.deleteAll(indexList);
            pageRepository.delete(page);
        }

        Connection connection = Jsoup.connect(url)
                .ignoreContentType(true)
                .userAgent(new UserAgent().getUserAgent())
                .referrer("https://www.google.com");

        try {
            Document document = connection.execute().parse();
            page = new PageEntity();
            page.setSiteID(site);
            page.setPath(url);
            page.setContent(String.valueOf(document));
            page.setCode(connection.response().statusCode());
            pageRepository.save(page);
            site.setStatus(Status.INDEXED);
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);

            Lemmatization lemmatization = new Lemmatization();
            Map<String, Integer> lemmas = lemmatization.getLemmas(document.text());
            List<LemmaEntity> lemmaEntityList = new ArrayList<>();
            List<SearchIndex> searchIndexList = new ArrayList<>();

            for (Map.Entry<String, Integer> lemma : lemmas.entrySet()) {
                LemmaEntity lemmaEntity = new LemmaEntity();
                lemmaEntity.setSiteID(site);
                lemmaEntity.setLemma(lemma.getKey());
                lemmaEntity.setFrequency(lemma.getValue());
                lemmaEntityList.add(lemmaEntity);

                SearchIndex searchIndex = new SearchIndex();
                searchIndex.setPageID(page);
                searchIndex.setLemmaID(lemmaEntity);
                searchIndex.setRank(lemma.getValue());
                searchIndexList.add(searchIndex);
            }
            lemmaRepository.saveAll(lemmaEntityList);
            indexRepository.saveAll(searchIndexList);
        } catch (IOException e) {
            System.out.println(url + "can't be parsed");
        }
    }
}