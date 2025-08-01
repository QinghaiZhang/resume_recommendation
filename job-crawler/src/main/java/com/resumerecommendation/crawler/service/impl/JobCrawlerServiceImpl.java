package com.resumerecommendation.crawler.service.impl;

import com.resumerecommendation.common.entity.JobPosition;
import com.resumerecommendation.common.entity.Resume;
import com.resumerecommendation.crawler.config.CrawlerConfig;
import com.resumerecommendation.crawler.service.JobCrawlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Service;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.processor.PageProcessor;
import us.codecraft.webmagic.selector.Html;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobCrawlerServiceImpl implements JobCrawlerService, PageProcessor {

    private final CrawlerConfig crawlerConfig;
    private final Map<String, Spider> spiders = new ConcurrentHashMap<>();
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private Site site = Site.me()
            .setRetryTimes(3)
            .setSleepTime(1000)
            .setTimeOut(10000);

    @Override
    public List<JobPosition> crawlJobsBasedOnResume(Resume resume, Integer maxResults) {
        return new ArrayList<>();
    }

    @Override
    public void startCrawling(String source) {
        if (isRunning.get()) {
            log.warn("Crawler is already running");
            return;
        }

        CrawlerConfig.JobSource jobSource = crawlerConfig.getSources().stream()
                .filter(s -> s.getName().equals(source))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid source: " + source));

        Spider spider = Spider.create(this)
                .addUrl(jobSource.getListUrl())
                .thread(crawlerConfig.getThreadCount());

        spiders.put(source, spider);
        isRunning.set(true);
        spider.start();
    }

    @Override
    public void stopCrawling() {
        isRunning.set(false);
        spiders.values().forEach(Spider::stop);
        spiders.clear();
    }

    @Override
    public String getCrawlerStatus() {
        return isRunning.get() ? "Running" : "Stopped";
    }

    @Override
    public List<JobPosition> searchJobs(String keyword, String city, Integer pageSize) {
        // 实现职位搜索逻辑
        return new ArrayList<>();
    }

    @Override
    public void updateExpiredJobs() {
        // 实现过期职位更新逻辑
    }

    @Override
    public void cleanInvalidJobs() {
        // 实现无效职位清理逻辑
    }

    @Override
    public void process(Page page) {
        String url = page.getUrl().toString();
        CrawlerConfig.JobSource source = findSourceByUrl(url);

        if (source == null) {
            log.error("Unknown source for URL: {}", url);
            return;
        }

        if (isListPage(url, source)) {
            processListPage(page, source);
        } else {
            processDetailPage(page, source);
        }
    }

    @Override
    public Site getSite() {
        return site;
    }

    private CrawlerConfig.JobSource findSourceByUrl(String url) {
        return crawlerConfig.getSources().stream()
                .filter(source -> url.startsWith(source.getBaseUrl()))
                .findFirst()
                .orElse(null);
    }

    private boolean isListPage(String url, CrawlerConfig.JobSource source) {
        return url.equals(source.getListUrl()) || url.contains("page=") || url.contains("pn=");
    }

    private void processListPage(Page page, CrawlerConfig.JobSource source) {
        Html html = page.getHtml();
        Map<String, String> selectors = source.getSelectors();

        // 提取职位详情页链接
        List<String> detailUrls = html.css(selectors.get("listItem"), "href").all();
        page.addTargetRequests(detailUrls);

        // 提取下一页链接
        String nextPageUrl = html.css(selectors.get("nextPage"), "href").get();
        if (nextPageUrl != null) {
            page.addTargetRequest(nextPageUrl);
        }
    }

    private void processDetailPage(Page page, CrawlerConfig.JobSource source) {
        if (source.getUseSelenium()) {
            processWithSelenium(page, source);
        } else {
            processWithWebMagic(page, source);
        }
    }

    private void processWithWebMagic(Page page, CrawlerConfig.JobSource source) {
        Html html = page.getHtml();
        Map<String, String> selectors = source.getSelectors();

        JobPosition position = new JobPosition();
        position.setTitle(html.css(selectors.get("title")).get());
        position.setCompany(html.css(selectors.get("company")).get());
        position.setLocation(html.css(selectors.get("location")).get());
        position.setDescription(html.css(selectors.get("description")).get());
        position.setSource(source.getName());
        position.setSourceUrl(page.getUrl().toString());
        position.setCrawlTime(LocalDateTime.now());

        // 解析薪资范围
        String salary = html.css(selectors.get("salary")).get();
        parseSalary(salary, position);

        // 解析所需技能
        List<String> skills = html.css(selectors.get("skills")).all();
        position.setRequiredSkills(skills);

        page.putField("job", position);
    }

    private void processWithSelenium(Page page, CrawlerConfig.JobSource source) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        WebDriver driver = new ChromeDriver(options);
        try {
            driver.get(page.getUrl().toString());
            Thread.sleep(2000); // 等待页面加载

            Map<String, String> selectors = source.getSelectors();
            JobPosition position = new JobPosition();
            position.setTitle(driver.findElement(By.cssSelector(selectors.get("title"))).getText());
            position.setCompany(driver.findElement(By.cssSelector(selectors.get("company"))).getText());
            position.setLocation(driver.findElement(By.cssSelector(selectors.get("location"))).getText());
            position.setDescription(driver.findElement(By.cssSelector(selectors.get("description"))).getText());
            position.setSource(source.getName());
            position.setSourceUrl(page.getUrl().toString());
            position.setCrawlTime(LocalDateTime.now());

            page.putField("job", position);
        } catch (Exception e) {
            log.error("Error processing page with Selenium: {}", page.getUrl(), e);
        } finally {
            driver.quit();
        }
    }

    private void parseSalary(String salary, JobPosition position) {
        if (salary == null || salary.isEmpty()) {
            return;
        }

        // 示例：解析"15k-30k"格式的薪资
        try {
            String[] parts = salary.toLowerCase().replace("k", "").split("-");
            if (parts.length == 2) {
                position.setSalaryMin(Integer.parseInt(parts[0].trim()) * 1000);
                position.setSalaryMax(Integer.parseInt(parts[1].trim()) * 1000);
            }
        } catch (Exception e) {
            log.warn("Failed to parse salary: {}", salary);
        }
    }
} 