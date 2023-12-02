package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.IndexingResponse;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.SiteRepository;
import searchengine.utils.SiteParser;

import java.time.LocalDateTime;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private final SitesList sites;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private boolean contains;
    private SiteEntity siteEntity;
    private ForkJoinPool forkJoinPool;

    @Override
    @SneakyThrows
    public IndexingResponse startIndexing() {
        indexRepository.deleteAll();
        lemmaRepository.deleteAll();
        pageRepository.deleteAll();
        siteRepository.deleteAll();

        if(isIndexing()) {
            return new IndexingResponse(false, "Индексация уже запущена");
        }
        else {
            for (Site site : sites.getSites()) {
                indexSite(site);
            }
        }
        return new IndexingResponse(true, "");
    }

    @Override
    public IndexingResponse stopIndexing() {
        if(!isIndexing()) {
            return new IndexingResponse(false, "Индексация не запущена");
        } else {
            forkJoinPool.shutdownNow();
            Iterable<SiteEntity> siteList = siteRepository.findAll();
            for (SiteEntity siteEntity : siteList) {
                if (siteEntity.getStatus().equals(Status.INDEXING)) {
                    siteEntity.setStatus(Status.FAILED);
                    siteEntity.setStatusTime(LocalDateTime.now());
                    siteEntity.setLastError("Процесс индексации остановлен");
                    siteRepository.save(siteEntity);
                }
            }
        }
        return new IndexingResponse(true, "Процесс индексации остановлен");
    }

    @Override
    @SneakyThrows
    public IndexingResponse indexPage(String url) {
        Site indexingSite = new Site();
        for(Site site : sites.getSites()) {
            if(url.contains(site.getUrl())) {
                contains = true;
                indexingSite = site;
                break;
            }
        }
        if(contains){
            boolean containsInRepository = false;
            for(SiteEntity site : siteRepository.findAll()){
                if(site.getName().equals(indexingSite.getName())){
                    siteEntity = site;
                    System.out.println("Сайт был найден в репозитории. Запущена пере индексация страницы");
                    containsInRepository = true;
                    break;
                }
            }

            if(!containsInRepository){
                System.out.println("Сайт не был найден в репозитории. Создается новый объект");
                siteEntity = new SiteEntity();
                siteEntity.setUrl(indexingSite.getUrl());
                siteEntity.setName(indexingSite.getName());
                siteEntity.setStatus(Status.INDEXING);
                siteEntity.setStatusTime(LocalDateTime.now());
                siteRepository.save(siteEntity);
            }
            return new IndexingResponse(true);
        }
        else {
            return new IndexingResponse(false,
                    "Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
        }
    }

    private void indexSite(Site site) {
        SiteEntity siteEntity = new SiteEntity();
        siteEntity.setName(site.getName());
        siteEntity.setUrl(site.getUrl());
        siteEntity.setStatus(Status.INDEXING);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteEntity);

        TreeSet<String> hrefList = new TreeSet<>();
        hrefList.add(site.getUrl());

        forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
        SiteParser siteParser = new SiteParser(site.getUrl(), hrefList, siteEntity,
                pageRepository, siteRepository, lemmaRepository, indexRepository);
        forkJoinPool.execute(siteParser);
        forkJoinPool.shutdown();

//        siteEntity.setStatus(Status.INDEXED);
//        siteEntity.setStatusTime(LocalDateTime.now());
//        siteRepository.save(siteEntity);

    }

    public boolean isIndexing() {
        Iterable<SiteEntity> siteList = siteRepository.findAll();
        for (SiteEntity site : siteList) {
            if (site.getStatus().equals(Status.INDEXING)) {
                return true;
            }
        }
        return false;
    }
}