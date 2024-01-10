package searchengine.utils;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.model.*;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.SiteRepository;
import java.util.*;
import java.util.concurrent.RecursiveAction;

@RequiredArgsConstructor
public class SiteParser extends RecursiveAction {
    private final String url;
    private final TreeSet<String> hrefList;
    private final SiteEntity siteEntity;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private Document document;

    @Override
    @SneakyThrows
    protected void compute() {

        Thread.sleep(250);
        Connection connection = Jsoup.connect(url)
                .ignoreContentType(true)
                .userAgent(new UserAgent().getUserAgent())
                .referrer("https://www.google.com");

        try {
            document = connection.execute().parse();
            PageEntity page = new PageEntity();
            page.setSiteID(siteEntity);
            page.setPath(url);
            page.setContent(String.valueOf(document));
            page.setCode(connection.response().statusCode());
            pageRepository.save(page);

            Lemmatization lemmatization = new Lemmatization();
            Map<String, Integer> lemmas = lemmatization.getLemmas(document.text());
            List<LemmaEntity> lemmaEntityList = new ArrayList<>();
            List<SearchIndex> searchIndexList = new ArrayList<>();

            for(Map.Entry<String, Integer> lemma : lemmas.entrySet()){

                LemmaEntity lemmaEntity = new LemmaEntity();
                lemmaEntity.setSiteID(siteEntity);
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

        }
        catch (HttpStatusException e) {
            System.out.println(url + " can't be parsed");
        }

        List<String> links = collectLinks(url);
        List<SiteParser> tasks = new ArrayList<>();
        for (String link : links) {
            if (!hrefList.contains(link) && !links.isEmpty()) {
                SiteParser siteParser = new SiteParser(
                        link,
                        hrefList,
                        siteEntity,
                        pageRepository,
                        siteRepository,
                        lemmaRepository,
                        indexRepository);
                siteParser.fork();
                hrefList.add(link);
                System.out.println("parsing " + link);
                tasks.add(siteParser);
            }
        }

        for (SiteParser task : tasks) {
            task.join();
        }
    }

    @SneakyThrows
    public synchronized List<String> collectLinks(String url) {
        List<String> linkList = new ArrayList<>();
        linkList.add(url);

        Elements links = document.select("a[href]");
        for (Element element : links) {
            String link = element.attr("abs:href");
            if (!link.startsWith(url)) continue;
            if (link.contains("#")) continue;
            if (link.endsWith(".shtml") ||
                    link.endsWith(".pdf") ||
                    link.endsWith(".xml") ||
                    link.endsWith("?main_click") ||
                    link.contains("?page=") ||
                    link.contains("?ref")) continue;
            if (linkList.contains(link)) continue;
            linkList.add(link);
        }
        return linkList;
    }
}