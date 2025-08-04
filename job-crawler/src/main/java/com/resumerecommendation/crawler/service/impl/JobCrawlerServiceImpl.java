package com.resumerecommendation.crawler.service.impl;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.resumerecommendation.common.entity.JobPosition;
import com.resumerecommendation.common.entity.Resume;
import com.resumerecommendation.crawler.config.CrawlerConfig;
import com.resumerecommendation.crawler.service.JobCrawlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.processor.PageProcessor;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class JobCrawlerServiceImpl implements JobCrawlerService, PageProcessor {

    private final CrawlerConfig crawlerConfig;
    private final Map<String, BrowserContext> browserContexts = new ConcurrentHashMap<>();
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    // 常见的用户代理字符串
    private static final String[] USER_AGENTS = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/5.37.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:89.0) Gecko/20100101 Firefox/89.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.1.1 Safari/605.1.15"
    };

    private Site site = Site.me()
            .setRetryTimes(3)
            .setSleepTime(1000)
            .setTimeOut(10000);

    @PostConstruct
    public void init() {
        // Playwright不需要像Selenium那样手动配置驱动
        log.info("Playwright crawler initialized");
    }

    @Override
    public List<JobPosition> crawlJobsBasedOnResume(Resume resume, Integer maxResults) {
        // 基于简历内容构建搜索关键词
        StringBuilder keywordBuilder = new StringBuilder();

        // 添加技能关键词
        if (resume.getSkills() != null && !resume.getSkills().isEmpty()) {
            keywordBuilder.append(String.join(" ", resume.getSkills()));
        }

        // 添加工作经历中的职位和公司信息
        if (resume.getWorkExperiences() != null) {
            for (int i = 0; i < resume.getWorkExperiences().size(); i++) {
                if (i > 0) keywordBuilder.append(" ");
                keywordBuilder.append(resume.getWorkExperiences().get(i).getJobTitle());
            }
        }

        // 添加教育背景中的专业信息
        if (resume.getEducation() != null) {
            keywordBuilder.append(" ").append(resume.getEducation());
        }

        String keyword = keywordBuilder.toString().trim();
        log.info("Crawling jobs based on resume with keyword: {}", keyword);

        // 使用现有搜索功能搜索相关职位
        return searchJobs(keyword, null, maxResults);
    }

    @Override
    public void startCrawling(String source) {
        log.info("Starting crawling process for source: {}", source);

        // 检查爬虫是否已经在运行
        if (isRunning.get()) {
            log.warn("Crawling is already running");
            return;
        }

        // 查找对应的爬虫配置
        CrawlerConfig.JobSource jobSource = crawlerConfig.getSources().stream()
                .filter(s -> s.getName().equals(source))
                .findFirst()
                .orElse(null);

        if (jobSource == null) {
            log.error("No configuration found for source: {}", source);
            throw new IllegalArgumentException("No configuration found for source: " + source);
        }

        Playwright playwright = null;
        Browser browser = null;
        BrowserContext context = null;

        try {
            log.info("Starting Playwright crawling for source: {} with URL: {}", source, jobSource.getListUrl());

            // 创建Playwright实例
            playwright = Playwright.create();

            // 随机选择用户代理
            Random random = new Random();
            String userAgent = USER_AGENTS[random.nextInt(USER_AGENTS.length)];

            // 创建浏览器并配置反反爬虫设置
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setSlowMo(50) // 减慢操作速度
                    .setArgs(List.of(
                        "--no-sandbox",
                        "--disable-setuid-sandbox",
                        "--disable-dev-shm-usage",
                        "--disable-accelerated-2d-canvas",
                        "--no-first-run",
                        "--no-zygote",
                        "--disable-gpu"
                    )));

            // 创建浏览器上下文并配置反反爬虫设置
            context = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent(userAgent)
                    .setViewportSize(1920, 1080)
                    .setExtraHTTPHeaders(Map.of("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")));

            // 添加避免被检测的脚本
            context.addInitScript("() => {" +
                "delete navigator.__proto__.webdriver;" +
                "window.chrome = {runtime: {}};" +
                "Object.defineProperty(navigator, 'plugins', {" +
                    "get: () => [1, 2, 3, 4, 5]" +
                "});" +
                "Object.defineProperty(navigator, 'languages', {" +
                    "get: () => ['zh-CN', 'zh', 'en']" +
                "});" +
            "}");

            browserContexts.put(source, context);

            // 创建页面
            Page page = context.newPage();

            // 设置页面加载超时
            page.setDefaultTimeout(30000);

            // 访问目标页面
            page.navigate(jobSource.getListUrl());

            // 等待页面加载
            page.waitForLoadState();

            // 随机等待，模拟人类行为
            randomWait(2000, 5000);

            // 处理页面内容
            processPageWithPlaywright(page, jobSource);

            log.info("Crawling started successfully for source: {}", source);
        } catch (Exception e) {
            log.error("Error starting crawling for source: {}", source, e);
            isRunning.set(false);
            throw new RuntimeException("Failed to start crawling for source: " + source, e);
        } finally {
            // 清理资源
            if (context != null) {
                try {
                    context.close();
                    browserContexts.remove(source);
                } catch (Exception e) {
                    log.warn("Error closing browser context for source: {}", source, e);
                }
            }
            if (browser != null) {
                try {
                    browser.close();
                } catch (Exception e) {
                    log.warn("Error closing browser for source: {}", source, e);
                }
            }
            if (playwright != null) {
                try {
                    playwright.close();
                } catch (Exception e) {
                    log.warn("Error closing playwright for source: {}", source, e);
                }
            }
        }
    }

    /**
     * 使用Playwright处理页面内容
     * @param page Playwright页面实例
     * @param jobSource 爬虫配置
     */
    private void processPageWithPlaywright(Page page, CrawlerConfig.JobSource jobSource) {
        try {
            log.info("Processing page with Playwright for source: {}", jobSource.getName());

            // 等待页面完全加载
            page.waitForLoadState();

            // 查找职位列表项 - 使用多种选择器尝试
            String[] selectors = {
                "[data-v-7a4b5b6e] .job-card-box",
                ".job-card-wrapper",
                "[class*='job'][class*='card']",
                ".job-list-item",
                ".job-card"
            };

            List<ElementHandle> jobElements = new ArrayList<>();

            for (String selector : selectors) {
                try {
                    log.debug("Trying selector: {}", selector);
                    jobElements = page.querySelectorAll(selector);
                    if (!jobElements.isEmpty()) {
                        log.info("Using selector {} found {} job elements", selector, jobElements.size());
                        break;
                    }
                } catch (Exception e) {
                    log.debug("Selector {} not found", selector);
                }
            }

            if (jobElements.isEmpty()) {
                log.warn("No job elements found with any selector");
                // 保存页面内容用于调试
                String content = page.content();
                log.debug("Page content length: {}", content.length());
                return;
            }

            log.info("Found {} job items on page", jobElements.size());

            // 处理每个职位项
            for (int i = 0; i < Math.min(jobElements.size(), 20); i++) { // 限制处理数量避免被封
                ElementHandle jobElement = jobElements.get(i);
                try {
                    JobPosition jobPosition = new JobPosition();
                    jobPosition.setSource(jobSource.getName());
                    jobPosition.setCrawlTime(LocalDateTime.now());

                    // 模拟人类浏览行为
                    if (i > 0 && i % 5 == 0) {
                        randomWait(1000, 3000);
                    }

                    // 提取职位标题
                    try {
                        String title = extractTextWithSelector(jobElement, "[data-v-7a4b5b6e] .job-name");
                        if (title == null || title.isEmpty()) {
                            title = extractTextWithSelector(jobElement, ".job-title .job-name");
                        }
                        if (title == null || title.isEmpty()) {
                            title = extractTextWithSelector(jobElement, ".job-name");
                        }
                        jobPosition.setTitle(title);
                        log.debug("Job title: {}", jobPosition.getTitle());
                    } catch (Exception e) {
                        log.warn("Failed to extract title for job item", e);
                    }

                    // 提取公司名称
                    try {
                        String company = extractTextWithSelector(jobElement, "[data-v-7a4b5b6e] .company-name");
                        if (company == null || company.isEmpty()) {
                            company = extractTextWithSelector(jobElement, ".company-info .name");
                        }
                        if (company == null || company.isEmpty()) {
                            company = extractTextWithSelector(jobElement, "[class*='company-name']");
                        }
                        jobPosition.setCompany(company);
                        log.debug("Company name: {}", jobPosition.getCompany());
                    } catch (Exception e) {
                        log.warn("Failed to extract company for job item", e);
                    }

                    // 提取工作地点
                    try {
                        String location = extractTextWithSelector(jobElement, "[data-v-7a4b5b6e] .job-area");
                        if (location == null || location.isEmpty()) {
                            location = extractTextWithSelector(jobElement, ".job-location");
                        }
                        if (location == null || location.isEmpty()) {
                            location = extractTextWithSelector(jobElement, ".info-desc");
                        }
                        if (location == null || location.isEmpty()) {
                            location = extractTextWithSelector(jobElement, "[class*='area']");
                        }
                        jobPosition.setLocation(location);
                        log.debug("Job location: {}", jobPosition.getLocation());
                    } catch (Exception e) {
                        log.warn("Failed to extract location for job item", e);
                    }

                    // 提取薪资范围
                    try {
                        String salary = extractTextWithSelector(jobElement, "[data-v-7a4b5b6e] .job-salary");
                        if (salary == null || salary.isEmpty()) {
                            salary = extractTextWithSelector(jobElement, ".salary");
                        }
                        if (salary == null || salary.isEmpty()) {
                            salary = extractTextWithSelector(jobElement, ".red");
                        }
                        if (salary == null || salary.isEmpty()) {
                            salary = extractTextWithSelector(jobElement, "[class*='salary']");
                        }

                        jobPosition.setDescription(salary); // 暂时将薪资信息放在描述字段中
                        log.debug("Salary: {}", salary);

                        // 解析薪资范围
                        parseSalaryRange(jobPosition, salary);
                    } catch (Exception e) {
                        log.warn("Failed to extract salary for job item", e);
                    }

                    // 提取经验要求
                    try {
                        String experience = extractTextWithSelector(jobElement, "[data-v-7a4b5b6e] .tag-list li:first-child");
                        if (experience == null || experience.isEmpty()) {
                            experience = extractTextWithSelector(jobElement, ".experience");
                        }
                        if (experience == null || experience.isEmpty()) {
                            // 尝试从tag-list中提取
                            List<ElementHandle> tags = jobElement.querySelectorAll(".tag-list li");
                            if (!tags.isEmpty()) {
                                ElementHandle firstTag = tags.get(0);
                                experience = firstTag.innerText();
                            }
                        }
                        // 尝试解析经验要求为数字
                        if (experience != null && !experience.isEmpty()) {
                            Pattern pattern = Pattern.compile("(\\d+)(?:-(\\d+))?年");
                            Matcher matcher = pattern.matcher(experience);
                            if (matcher.find()) {
                                try {
                                    jobPosition.setRequiredExperience(Integer.parseInt(matcher.group(1)));
                                } catch (NumberFormatException e) {
                                    log.debug("Failed to parse experience as number: {}", experience);
                                }
                            }
                        }
                        log.debug("Experience: {}", experience);
                    } catch (Exception e) {
                        log.warn("Failed to extract experience for job item", e);
                    }

                    log.debug("Extracted job position: {}", jobPosition);
                } catch (Exception e) {
                    log.error("Error processing job item", e);
                }
            }

            // 查找下一页链接
            try {
                String nextPageSelector = jobSource.getSelectors().get("nextPage");
                ElementHandle nextButton = page.querySelector(nextPageSelector);

                if (nextButton != null && nextButton.isVisible()) {
                    log.info("Navigating to next page");
                    nextButton.click();
                    // 等待页面加载完成
                    page.waitForLoadState();
                    randomWait(3000, 6000);
                    // 递归处理下一页
                    processPageWithPlaywright(page, jobSource);
                } else {
                    log.info("No more pages to crawl");
                }
            } catch (Exception e) {
                log.info("No next page button found or reached the end of pagination: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.error("Error processing page with Playwright", e);
        }
    }

    /**
     * 使用选择器从元素中提取文本
     * @param element 父元素
     * @param selector CSS选择器
     * @return 提取的文本，如果未找到则返回null
     */
    private String extractTextWithSelector(ElementHandle element, String selector) {
        try {
            ElementHandle subElement = element.querySelector(selector);
            if (subElement != null) {
                String text = subElement.innerText();
                return text != null ? text.trim() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract text with selector: {}", selector);
        }
        return null;
    }

    /**
     * 解析薪资范围
     * @param jobPosition 职位对象
     * @param salary 薪资字符串
     */
    private void parseSalaryRange(JobPosition jobPosition, String salary) {
        if (salary == null || salary.isEmpty()) {
            return;
        }

        try {
            // 匹配薪资范围，例如 "25-40K·12薪" 或 "15-25K"
            Pattern pattern = Pattern.compile("(\\d+)(?:-(\\d+))?K");
            Matcher matcher = pattern.matcher(salary);

            if (matcher.find()) {
                try {
                    int minSalary = Integer.parseInt(matcher.group(1));
                    jobPosition.setSalaryMin(minSalary * 1000); // 转换为元

                    if (matcher.group(2) != null) {
                        int maxSalary = Integer.parseInt(matcher.group(2));
                        jobPosition.setSalaryMax(maxSalary * 1000); // 转换为元
                    } else {
                        jobPosition.setSalaryMax(minSalary * 1000); // 如果没有范围，最大值等于最小值
                    }
                } catch (NumberFormatException e) {
                    log.debug("Failed to parse salary numbers: {}", salary);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse salary range: {}", salary);
        }
    }

    /**
     * 随机等待一段时间，模拟人类行为
     * @param min 最小等待时间（毫秒）
     * @param max 最大等待时间（毫秒）
     */
    private void randomWait(int min, int max) {
        try {
            Random random = new Random();
            int waitTime = random.nextInt(max - min + 1) + min;
            Thread.sleep(waitTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Random wait interrupted", e);
        }
    }

    @Override
    public void stopCrawling() {
        log.info("Stopping all crawling processes");
        isRunning.set(false);

        // 关闭所有浏览器上下文
        for (Map.Entry<String, BrowserContext> entry : browserContexts.entrySet()) {
            try {
                entry.getValue().close();
                log.info("Closed browser context for source: {}", entry.getKey());
            } catch (Exception e) {
                log.error("Error closing browser context for source: {}", entry.getKey(), e);
            }
        }

        browserContexts.clear();
        log.info("All crawling processes stopped");
    }

    @Override
    public String getCrawlerStatus() {
        if (isRunning.get()) {
            return "RUNNING (" + browserContexts.size() + " browser contexts active)";
        } else {
            return "STOPPED";
        }
    }

    @Override
    public List<JobPosition> searchJobs(String keyword, String city, Integer pageSize) {
        List<JobPosition> jobs = new ArrayList<>();

        // 如果是Boss直聘的搜索，使用Playwright爬取
        CrawlerConfig.JobSource jobSource = crawlerConfig.getSources().stream()
            .filter(s -> "zhipin".equals(s.getName()))
            .findFirst()
            .orElse(null);

        if (jobSource != null) {
            String searchUrl = jobSource.getListUrl();
            if (keyword != null && !keyword.isEmpty()) {
                searchUrl += "?query=" + keyword;
                if (city != null && !city.isEmpty()) {
                    searchUrl += "&city=" + city;
                }
            }

            Playwright playwright = null;
            Browser browser = null;
            BrowserContext context = null;

            try {
                log.info("Searching jobs with Playwright for URL: {}", searchUrl);

                // 创建Playwright实例
                playwright = Playwright.create();

                // 随机选择用户代理
                Random random = new Random();
                String userAgent = USER_AGENTS[random.nextInt(USER_AGENTS.length)];

                // 创建浏览器并配置反反爬虫设置
                browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                        .setHeadless(true)
                        .setSlowMo(50)
                        .setArgs(List.of(
                            "--no-sandbox",
                            "--disable-setuid-sandbox",
                            "--disable-dev-shm-usage",
                            "--disable-accelerated-2d-canvas",
                            "--no-first-run",
                            "--no-zygote",
                            "--disable-gpu"
                        )));

                // 创建浏览器上下文并配置反反爬虫设置
                context = browser.newContext(new Browser.NewContextOptions()
                        .setUserAgent(userAgent)
                        .setViewportSize(1920, 1080)
                        .setExtraHTTPHeaders(Map.of("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")));

                // 添加避免被检测的脚本
                context.addInitScript("() => {" +
                    "delete navigator.__proto__.webdriver;" +
                    "window.chrome = {runtime: {}};" +
                    "Object.defineProperty(navigator, 'plugins', {" +
                        "get: () => [1, 2, 3, 4, 5]" +
                    "});" +
                    "Object.defineProperty(navigator, 'languages', {" +
                        "get: () => ['zh-CN', 'zh', 'en']" +
                    "});" +
                "}");

                // 创建页面
                Page page = context.newPage();

                // 设置页面加载超时
                page.setDefaultTimeout(30000);

                // 访问目标页面
                page.navigate(searchUrl);

                // 等待页面加载
                page.waitForLoadState();

                // 随机等待，模拟人类行为
                randomWait(3000, 5000);

                // 处理页面内容，获取职位列表
                jobs = extractJobsFromPage(page);

                log.info("Found {} jobs from search", jobs.size());
            } catch (Exception e) {
                log.error("Error searching jobs with Playwright", e);
            } finally {
                // 清理资源
                if (context != null) {
                    try {
                        context.close();
                    } catch (Exception e) {
                        log.warn("Error closing browser context", e);
                    }
                }
                if (browser != null) {
                    try {
                        browser.close();
                    } catch (Exception e) {
                        log.warn("Error closing browser", e);
                    }
                }
                if (playwright != null) {
                    try {
                        playwright.close();
                    } catch (Exception e) {
                        log.warn("Error closing playwright", e);
                    }
                }
            }
        }

        // 如果指定了最大结果数，截取列表
        if (pageSize != null && pageSize > 0 && jobs.size() > pageSize) {
            jobs = jobs.subList(0, pageSize);
        }

        return jobs;
    }

    /**
     * 从页面中提取职位信息
     * @param page 页面对象
     * @return 职位列表
     */
    private List<JobPosition> extractJobsFromPage(Page page) {
        List<JobPosition> jobs = new ArrayList<>();

        try {
            // 查找职位列表项 - 使用多种选择器尝试
            String[] selectors = {
                "[data-v-7a4b5b6e] .job-card-box",
                ".job-card-wrapper",
                "[class*='job'][class*='card']",
                ".job-list-item",
                ".job-card"
            };

            List<ElementHandle> jobElements = new ArrayList<>();

            for (String selector : selectors) {
                try {
                    log.debug("Trying selector: {}", selector);
                    jobElements = page.querySelectorAll(selector);
                    if (!jobElements.isEmpty()) {
                        log.info("Using selector {} found {} job elements", selector, jobElements.size());
                        break;
                    }
                } catch (Exception e) {
                    log.debug("Selector {} not found", selector);
                }
            }

            if (jobElements.isEmpty()) {
                log.warn("No job elements found with any selector");
                return jobs;
            }

            // 处理找到的职位项
            for (int i = 0; i < Math.min(jobElements.size(), 20); i++) { // 限制处理数量
                ElementHandle jobElement = jobElements.get(i);
                try {
                    JobPosition jobPosition = new JobPosition();
                    jobPosition.setSource("zhipin");
                    jobPosition.setCrawlTime(LocalDateTime.now());

                    // 提取职位标题
                    String title = extractTextWithSelector(jobElement, "[data-v-7a4b5b6e] .job-name");
                    if (title == null || title.isEmpty()) {
                        title = extractTextWithSelector(jobElement, ".job-title .job-name");
                    }
                    if (title == null || title.isEmpty()) {
                        title = extractTextWithSelector(jobElement, ".job-name");
                    }
                    jobPosition.setTitle(title);

                    // 提取公司名称
                    String company = extractTextWithSelector(jobElement, "[data-v-7a4b5b6e] .company-name");
                    if (company == null || company.isEmpty()) {
                        company = extractTextWithSelector(jobElement, ".company-info .name");
                    }
                    if (company == null || company.isEmpty()) {
                        company = extractTextWithSelector(jobElement, "[class*='company-name']");
                    }
                    jobPosition.setCompany(company);

                    // 提取工作地点
                    String location = extractTextWithSelector(jobElement, "[data-v-7a4b5b6e] .job-area");
                    if (location == null || location.isEmpty()) {
                        location = extractTextWithSelector(jobElement, ".job-location");
                    }
                    if (location == null || location.isEmpty()) {
                        location = extractTextWithSelector(jobElement, ".info-desc");
                    }
                    if (location == null || location.isEmpty()) {
                        location = extractTextWithSelector(jobElement, "[class*='area']");
                    }
                    jobPosition.setLocation(location);

                    // 提取薪资范围
                    String salary = extractTextWithSelector(jobElement, "[data-v-7a4b5b6e] .job-salary");
                    if (salary == null || salary.isEmpty()) {
                        salary = extractTextWithSelector(jobElement, ".salary");
                    }
                    if (salary == null || salary.isEmpty()) {
                        salary = extractTextWithSelector(jobElement, ".red");
                    }
                    if (salary == null || salary.isEmpty()) {
                        salary = extractTextWithSelector(jobElement, "[class*='salary']");
                    }

                    jobPosition.setDescription(salary);
                    parseSalaryRange(jobPosition, salary);

                    // 提取经验要求
                    String experience = extractTextWithSelector(jobElement, "[data-v-7a4b5b6e] .tag-list li:first-child");
                    if (experience == null || experience.isEmpty()) {
                        experience = extractTextWithSelector(jobElement, ".experience");
                    }
                    if (experience == null || experience.isEmpty()) {
                        // 尝试从tag-list中提取
                        List<ElementHandle> tags = jobElement.querySelectorAll(".tag-list li");
                        if (!tags.isEmpty()) {
                            ElementHandle firstTag = tags.get(0);
                            experience = firstTag.innerText();
                        }
                    }

                    // 尝试解析经验要求为数字
                    if (experience != null && !experience.isEmpty()) {
                        Pattern pattern = Pattern.compile("(\\d+)(?:-(\\d+))?年");
                        Matcher matcher = pattern.matcher(experience);
                        if (matcher.find()) {
                            try {
                                jobPosition.setRequiredExperience(Integer.parseInt(matcher.group(1)));
                            } catch (NumberFormatException e) {
                                log.debug("Failed to parse experience as number: {}", experience);
                            }
                        }
                    }

                    jobs.add(jobPosition);
                    log.debug("Extracted job: {}", jobPosition.getTitle());
                } catch (Exception e) {
                    log.error("Error processing job item", e);
                }
            }
        } catch (Exception e) {
            log.error("Error extracting jobs from page", e);
        }

        return jobs;
    }

    @Override
    public void updateExpiredJobs() {
        // TODO: 实现更新过期职位逻辑
    }

    @Override
    public void cleanInvalidJobs() {
        // TODO: 实现清理无效职位逻辑
    }

    @Override
    public void process(us.codecraft.webmagic.Page page) {
        // WebMagic处理方法，现在主要使用Playwright
        log.info("Page processed by WebMagic (not used in Playwright implementation)");
    }

    @Override
    public Site getSite() {
        return site;
    }
}