package main.builders;

import lombok.extern.slf4j.Slf4j;
import main.apiResponses.StatisticsResponse;
import main.config.Props;
import main.model.Site;
import main.repository.Repos;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
public class SiteBuilder implements Runnable {
    public static final boolean IS_INDEXING = true;
    private static ExecutorService executor;
    final static int forSitesThreadNumber =
            Props.getInst().getForSitesThreadNumber();
    private static final ConcurrentHashMap<String, Site>
            indexingSites = new ConcurrentHashMap<>();
    private Site site;
    private final Set<String> viewedPages;
    private final ConcurrentLinkedQueue<String> lastNodes;
    private final CopyOnWriteArraySet<String> forbiddenNodes;

    public static ConcurrentHashMap<String, Site> getIndexingSites() {
        return indexingSites;
    }

    public ConcurrentLinkedQueue<String> getLastNodes() {
        return lastNodes;
    }

    public CopyOnWriteArraySet<String> getForbiddenNodes() {
        return forbiddenNodes;
    }

    public Set<String> getViewedPages() {
        return viewedPages;
    }

    private static final boolean SINGLE_SITE_IS_INDEXING = true;
    private static boolean stopping = false;

    public static boolean isStopping() {
        return stopping;
    }

    public static void setStopping(boolean stopping) {
        SiteBuilder.stopping = stopping;
    }

    public SiteBuilder(String siteUrl) {
        lastNodes = new ConcurrentLinkedQueue<>();
        forbiddenNodes = new CopyOnWriteArraySet<>();
        viewedPages = new HashSet<>();

        Optional<Site> indexingSite =
                Repos.siteRepo.findByUrlAndType(siteUrl, Site.INDEXING);
        if (!indexingSite.isEmpty()) {
            indexingSite.get().setType(Site.REMOVING);
            Repos.siteRepo.saveAndFlush(indexingSite.get());
        }

        site = new Site();
        site.setName(Props.SiteProps.getNameByUrl(siteUrl));
        site.setUrl(siteUrl);
        site.setStatusTime(LocalDateTime.now());
        site.setSiteBuilder(this);
        site.setType(Site.INDEXING);

        Repos.siteRepo.saveAndFlush(site);
    }

    @Override
    public void run() {
        log.info("Сайт \"" + site.getName() + "\" индексируется");
        buildPagesLemmasAndIndices();

        if (isStopping()) {
            Site indexingSite = Repos.siteRepo.findByNameAndType(site.getName(), Site.INDEXING)
                    .orElse(null);
            if (indexingSite != null) {
                indexingSite.setType(Site.REMOVING);
                Repos.siteRepo.saveAndFlush(indexingSite);
            }
            log.info("Индексация сайта \"" + site.getName() + "\" прервана");
        }

        indexingSites.remove(site.getUrl());
        if (indexingSites.isEmpty()) {
            stopping = false;
        }
    }

    private void buildPagesLemmasAndIndices() {
        long begin = System.currentTimeMillis();
        Site prevSite;
        PagesOfSiteBuilder.build(site);
        if (isStopping()) {
            return;
        }

        IndexBuilder.build(site);
        if (isStopping()) {
            return;
        }

        log.info(IndexBuilder.TABS + "Сайт \"" + site.getName() + "\" построен за " +
                (System.currentTimeMillis() - begin) / 1000 + " сек");

        setCurrentSiteAsWorking();
    }

    private void setCurrentSiteAsWorking() {
        Site prevSite = Repos.siteRepo.findByNameAndType(site.getName(), Site.INDEXED)
                .orElse(null);
        if (prevSite == null) {
            prevSite = Repos.siteRepo.findByNameAndType(site.getName(), Site.FAILED)
                    .orElse(null);
        }
        if (prevSite != null) {
            prevSite.setType(Site.REMOVING);
            Repos.siteRepo.saveAndFlush(prevSite);
        }

        if (site.getLastError().isEmpty()) {
            site.setType(Site.INDEXED);
        } else {
            site.setType(Site.FAILED);
        }
        Repos.siteRepo.saveAndFlush(site);

        synchronized(Site.REMOVING) {
            Repos.siteRepo.deleteByType(Site.REMOVING);
        }
    }

    public static void buildSite(String siteUrl) {
        synchronized (Executors.class) {
            if (executor == null) {
                executor = Executors.newFixedThreadPool(forSitesThreadNumber);
            }
        }

        SiteBuilder siteBuilder = new SiteBuilder(siteUrl);

        Site processingSite = indexingSites.putIfAbsent(siteUrl, siteBuilder.site);
        if (processingSite != null) {
            return;
        }

        executor.execute(siteBuilder);
    }

    public static boolean buildAllSites() {
        if (!indexingSites.isEmpty()) {
            return IS_INDEXING;
        }

        List<Props.SiteProps> sitePropsList = Props.getInst().getSites();
        for (var siteProps : sitePropsList) {
            buildSite(siteProps.getUrl());
        }
        return !IS_INDEXING;
    }


    public static void buildSingleSite(String url) {
        String siteName = Props.SiteProps.getNameByUrl(url);
        if (siteName.equals("")) {
            return;
        }

        buildSite(url);
    }

    public static boolean stopIndexing() {
        if (indexingSites.isEmpty()) {
            return !IS_INDEXING;
        }

        setStopping(true);

        return IS_INDEXING;
    }

    public static StatisticsResponse getStatistics() {
        return new StatisticsResponse();
    }
}
