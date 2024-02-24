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
import searchengine.utils.PageParser;
import searchengine.utils.SiteParser;
import java.time.LocalDateTime;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private final SitesList sites;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    public static volatile boolean inProgress;
    private static volatile boolean isStopped;
    private boolean contains;
    private SiteEntity siteEntity;
    private ForkJoinPool forkJoinPool;

    @Override
    @SneakyThrows
    public IndexingResponse startIndexing() {
        if (inProgress) {
            return new IndexingResponse(false, "Индексация уже запущена");
        } else {
            inProgress = true;
            new Thread(this::indexSite).start();
            return new IndexingResponse(true);
        }
    }

    @Override
    public IndexingResponse stopIndexing() {
        if (!inProgress) {
            return new IndexingResponse(false, "Индексация не запущена");
        } else {
            inProgress = false;
            isStopped = true;
        }
        forkJoinPool.shutdownNow();
        Iterable<SiteEntity> siteList = siteRepository.findAll();
        for (SiteEntity site : siteList) {
            if (site.getStatus() == Status.INDEXING) {
                site.setStatus(Status.FAILED);
                site.setStatusTime(LocalDateTime.now());
                site.setLastError("Индексация остановлена пользователем");
                siteRepository.save(site);
            }
        }
        return new IndexingResponse(true);
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

            PageParser pageParser = new PageParser(siteEntity, url, pageRepository, siteRepository,
                    lemmaRepository, indexRepository);
            pageParser.parsePage();
            return new IndexingResponse(true);
        }
        else {
            return new IndexingResponse(false,
                    "Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
        }
    }

    public IndexingResponse indexSite() {
        siteRepository.deleteAll();
        pageRepository.deleteAll();
        lemmaRepository.deleteAll();
        indexRepository.deleteAll();

        for (Site site : sites.getSites()) {
            SiteEntity siteEntity = new SiteEntity();
            siteEntity.setName(site.getName());
            siteEntity.setUrl(site.getUrl());
            siteEntity.setStatus(Status.INDEXING);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteEntity);

            SiteParser siteParser = new SiteParser(site.getUrl(),
                    siteEntity,
                    pageRepository, siteRepository, lemmaRepository, indexRepository);
            forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
            forkJoinPool.execute(siteParser);
            forkJoinPool.shutdown();

            try {
                forkJoinPool.awaitTermination(1, TimeUnit.HOURS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            if (isStopped) {
                isStopped = false;
                return new IndexingResponse(false, "Индексация остановлена пользователем");
            }

            siteEntity.setStatus(Status.INDEXED);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteEntity);
        }

        inProgress = false;
        return new IndexingResponse(true);
    }
}