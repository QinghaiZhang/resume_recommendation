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

    private Site site = Site.me()
            .setRetryTimes(3)
            .setSleepTime(2000)
            .setTimeOut(15000)
            .setCharset("UTF-8")
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .addHeader("Accept-Encoding", "gzip, deflate, br")
            .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .addHeader("Connection", "keep-alive")
            .addHeader("Referer", "https://www.zhipin.com/")
            .addHeader("DNT", "1")
            .addHeader("Upgrade-Insecure-Requests", "1");

    // 常见的用户代理字符串
    private static final String[] USER_AGENTS = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:127.0) Gecko/20100101 Firefox/127.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36 Edg/127.0.0.0"
    };

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
                    .setHeadless(false) // 默认设置为false，方便调试
                    .setSlowMo(100) // 减慢操作速度
                    .setArgs(List.of(
                        "--no-sandbox",
                        "--disable-setuid-sandbox",
                        "--disable-dev-shm-usage",
                        "--disable-accelerated-2d-canvas",
                        "--no-first-run",
                        "--no-zygote",
                        "--disable-gpu",
                        "--disable-web-security",
                        "--disable-features=IsolateOrigins,site-per-process",
                        "--enable-features=NetworkService,NetworkServiceInProcess"
                    )));

            // 创建浏览器上下文并配置反反爬虫设置
            context = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent(userAgent)
                    .setViewportSize(1920, 1080)
                    .setExtraHTTPHeaders(Map.of(
                        "Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8",
                        "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
                        "Accept-Encoding", "gzip, deflate, br",
                        "Referer", "https://www.zhipin.com/",
                        "DNT", "1",
                        "Connection", "keep-alive",
                        "Upgrade-Insecure-Requests", "1"
                    ))
                    .setGeolocation(31.2304, 121.4737) // 设置地理位置（上海）纬度、经度
                    .setLocale("zh-CN")
                    .setTimezoneId("Asia/Shanghai"));

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
                // 添加更多浏览器指纹伪装
                "Object.defineProperty(navigator, 'platform', {" +
                    "get: () => 'Win32'" +
                "});" +
                "Object.defineProperty(navigator, 'deviceMemory', {" +
                    "get: () => 8" +
                "});" +
                "Object.defineProperty(navigator, 'hardwareConcurrency', {" +
                    "get: () => 4" +
                "});" +
                "Object.defineProperty(navigator, 'cookieEnabled', {" +
                    "get: () => true" +
                "});" +
                "Object.defineProperty(navigator, 'doNotTrack', {" +
                    "get: () => null" +
                "});" +
                // 伪装canvas指纹
                "const canvas = document.createElement('canvas');" +
                "const ctx = canvas.getContext('2d');" +
                "ctx.textBaseline = 'top';" +
                "ctx.font = '14px Arial';" +
                "ctx.fillText('test', 2, 2);" +
                "const originalToDataURL = canvas.toDataURL;" +
                "canvas.toDataURL = function(type, quality) {" +
                    "if (type === 'image/png') {" +
                        "return 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==';" +
                    "} else {" +
                        "return originalToDataURL.call(this, type, quality);" +
                    "}" +
                "};" +
            "}");

            browserContexts.put(source, context);

            // 创建页面
            Page page = context.newPage();

            // 设置页面加载超时
            page.setDefaultTimeout(30000);
            
            // 设置页面加载状态
            page.setDefaultNavigationTimeout(30000);

            // 访问目标页面
            page.navigate(jobSource.getListUrl());

            // 等待页面加载
            page.waitForLoadState();
            
            // 检查是否被重定向到安全检查页面
            if (page.url().contains("security-check")) {
                log.warn("Detected security check page. Waiting for manual verification or trying to bypass...");
                // 等待更长时间，希望安全检查能自动通过
                randomWait(10000, 15000);
                
                // 检查是否仍然在安全检查页面
                if (page.url().contains("security-check")) {
                    log.error("Still on security check page. Manual intervention may be required.");
                    // 尝试重新加载页面
                    page.reload();
                    page.waitForLoadState();
                    randomWait(5000, 10000);
                }
            }

            // 随机等待，模拟人类行为
            randomWait(3000, 6000);

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
            
            // 额外等待确保动态内容加载完成
            randomWait(5000, 8000);

            // 查找职位列表项 - 使用多种选择器尝试
            String[] selectors = {
                "[data-v-7a4b5b6e] .job-card-box",
                ".job-card-wrapper",
                "[class*='job'][class*='card']",
                ".job-list-item",
                ".job-card",
                "[ka*='search_list_job']",
                ".job-primary", 
                ".job-item",
                "[data-lg-tj-id]",
                "[data-itemid]"
            };

            List<ElementHandle> jobElements = new ArrayList<>();

            for (String selector : selectors) {
                try {
                    log.debug("Trying selector: {}", selector);
                    jobElements = page.querySelectorAll(selector);
                    if (!jobElements.isEmpty()) {
                        log.info("Using selector {} found {} job elements", selector, jobElements.size());
                        break;
                    } else {
                        log.debug("Selector {} returned no elements", selector);
                    }
                } catch (Exception e) {
                    log.debug("Selector {} not found or caused exception: {}", selector, e.getMessage());
                }
            }

            // 如果仍然没有找到元素，记录页面信息用于调试
            if (jobElements.isEmpty()) {
                log.warn("No job elements found with any selector");
                
                // 记录页面标题
                String title = page.title();
                log.info("Page title: {}", title);
                
                // 记录页面URL
                String url = page.url();
                log.info("Page URL: {}", url);
                
                // 检查是否是验证码页面
                if (title.contains("验证码") || title.contains("验证") || title.contains("captcha")) {
                    log.error("Encountered CAPTCHA page. This is likely due to anti-crawling measures.");
                }
                
                // 记录部分页面内容用于调试
                String content = page.content();
                log.debug("Page content length: {}", content.length());
                
                // 检查页面内容中的关键字
                boolean hasJobContent = content.contains("job") || content.contains("职位") || content.contains("招聘");
                boolean hasSalaryContent = content.contains("salary") || content.contains("薪资");
                boolean hasCompanyContent = content.contains("company") || content.contains("公司");
                boolean hasCaptcha = content.contains("captcha") || content.contains("验证码");
                
                log.info("Page content analysis - Jobs: {}, Salaries: {}, Companies: {}, CAPTCHA: {}", 
                    hasJobContent, hasSalaryContent, hasCompanyContent, hasCaptcha);
                
                // 截取部分内容记录日志（避免日志过大）
                if (content.length() > 5000) {
                    log.debug("Page content (first 5000 chars): {}", content.substring(0, 5000));
                } else {
                    log.debug("Page content: {}", content);
                }
                
                // 尝试查找页面上的所有元素，看是否存在职位相关的关键字
                try {
                    List<ElementHandle> allElements = page.querySelectorAll("*");
                    log.info("Total elements on page: {}", allElements.size());
                    
                    // 查找包含职位相关关键字的元素
                    int jobRelatedElements = 0;
                    for (ElementHandle element : allElements) {
                        try {
                            String innerText = element.innerText();
                            if (innerText != null && 
                                (innerText.contains("job") || 
                                 innerText.contains("职位") || 
                                 innerText.contains("招聘") || 
                                 innerText.contains("salary") ||
                                 innerText.contains("薪资") ||
                                 innerText.contains("company") ||
                                 innerText.contains("公司"))) {
                                jobRelatedElements++;
                                log.debug("Found job-related element with text: {}", 
                                    innerText.length() > 100 ? innerText.substring(0, 100) + "..." : innerText);
                            }
                        } catch (Exception e) {
                            // 忽略单个元素的异常
                        }
                    }
                    log.info("Found {} job-related elements on page", jobRelatedElements);
                    
                    // 如果找不到任何职位相关元素，可能是页面结构发生了变化
                    if (jobRelatedElements == 0) {
                        log.warn("No job-related elements found. The page structure may have changed or the search returned no results.");
                    }
                } catch (Exception e) {
                    log.warn("Error while analyzing all page elements", e);
                }
                
                return;
            }

            log.info("Found {} job items on page", jobElements.size());

            // 处理每个职位项
            for (int i = 0; i < Math.min(jobElements.size(), 20); i++) { // 限制处理数量避免被封
                ElementHandle jobElement = jobElements.get(i);
                log.debug("Processing job item {}/{}", i+1, jobElements.size());
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
                        log.debug("Extracting title for job item {}/{}", i+1, jobElements.size());
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
                        log.debug("Extracting company for job item {}/{}", i+1, jobElements.size());
                        String company = extractTextWithSelector(jobElement, "[data-v-7a4b5b6e] .company-name");
                        if (company == null || company.isEmpty()) {
                            company = extractTextWithSelector(jobElement, ".company-info .name");
                        }
                        if (company == null || company.isEmpty()) {
                            company = extractTextWithSelector(jobElement, "[class*='company-name']");
                        }
                        if (company == null || company.isEmpty()) {
                            company = extractTextWithSelector(jobElement, ".company-title");
                        }
                        if (company == null || company.isEmpty()) {
                            company = extractTextWithSelector(jobElement, ".company-name");
                        }
                        jobPosition.setCompany(company);
                        log.debug("Company name: {}", jobPosition.getCompany());
                    } catch (Exception e) {
                        log.warn("Failed to extract company for job item", e);
                    }

                    // 提取工作地点
                    try {
                        log.debug("Extracting location for job item {}/{}", i+1, jobElements.size());
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
                        if (location == null || location.isEmpty()) {
                            location = extractTextWithSelector(jobElement, ".area");
                        }
                        if (location == null || location.isEmpty()) {
                            location = extractTextWithSelector(jobElement, ".job-area");
                        }
                        jobPosition.setLocation(location);
                        log.debug("Job location: {}", jobPosition.getLocation());
                    } catch (Exception e) {
                        log.warn("Failed to extract location for job item", e);
                    }

                    // 提取薪资范围
                    try {
                        log.debug("Extracting salary for job item {}/{}", i+1, jobElements.size());
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
                        if (salary == null || salary.isEmpty()) {
                            salary = extractTextWithSelector(jobElement, ".salary-text");
                        }
                        if (salary == null || salary.isEmpty()) {
                            salary = extractTextWithSelector(jobElement, ".job-salary");
                        }
                        
                        // 设置薪资信息
                        jobPosition.setSalary(salary);
                        log.debug("Salary: {}", salary);

                        // 解析薪资范围
                        parseSalaryRange(jobPosition, salary);
                        log.debug("Parsed salary range: {}-{}", jobPosition.getSalaryMin(), jobPosition.getSalaryMax());
                    } catch (Exception e) {
                        log.warn("Failed to extract salary for job item", e);
                    }

                    // 提取职位描述
                    log.debug("Extracting description for job item {}/{}", i+1, jobElements.size());
                    String description = extractTextWithSelector(jobElement, "[data-v-7a4b5b6e] .job-detail");
                    if (description == null || description.isEmpty()) {
                        description = extractTextWithSelector(jobElement, ".job-detail");
                    }
                    if (description == null || description.isEmpty()) {
                        description = extractTextWithSelector(jobElement, ".job-description");
                    }
                    if (description == null || description.isEmpty()) {
                        description = extractTextWithSelector(jobElement, ".job-desc");
                    }
                    if (description == null || description.isEmpty()) {
                        description = extractTextWithSelector(jobElement, ".text-desc");
                    }
                    if (description == null || description.isEmpty()) {
                        description = extractTextWithSelector(jobElement, ".desc");
                    }
                    if (description == null || description.isEmpty()) {
                        description = extractTextWithSelector(jobElement, ".job-card-body");
                    }
                    if (description == null || description.isEmpty()) {
                        description = extractTextWithSelector(jobElement, ".job-card-wrapper .info-desc");
                    }
                    if (description == null || description.isEmpty()) {
                        description = extractTextWithSelector(jobElement, "[data-v-7a4b5b6e] .info-desc");
                    }
                    
                    // 如果仍然没有找到描述，尝试获取整个职位卡片的文本内容
                    if (description == null || description.isEmpty()) {
                        try {
                            description = jobElement.innerText();
                            log.debug("Using full job element text as description: {}", 
                                description != null ? (description.length() > 200 ? description.substring(0, 200) + "..." : description) : "null");
                        } catch (Exception e) {
                            log.warn("Failed to extract full job element text", e);
                        }
                    }
                    
                    if (description != null && !description.isEmpty()) {
                        // 验证描述内容不等于薪资信息
                        String salaryText1 = extractTextWithSelector(jobElement, "[data-v-7a4b5b6e] .job-salary");
                        String salaryText2 = extractTextWithSelector(jobElement, ".job-salary");
                        
                        boolean isDescriptionValid = true;
                        if (description.equals(salaryText1) || description.equals(salaryText2)) {
                            log.debug("Description is same as salary, ignoring");
                            isDescriptionValid = false;
                        }
                        
                        // 检查描述是否过短
                        if (isDescriptionValid && description.length() < 10) {
                            log.debug("Description is too short (less than 10 chars): {}", description);
                            isDescriptionValid = false;
                        }
                        
                        if (isDescriptionValid) {
                            jobPosition.setDescription(description);
                            log.debug("Job description set: {}", 
                                description.length() > 200 ? description.substring(0, 200) + "..." : description);
                        }
                    } else {
                        log.debug("No valid description found for job item");
                    }
                    log.debug("Final job description: {}", jobPosition.getDescription());
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
                log.debug("Selector '{}' found text: {}", selector, text);
                return text != null ? text.trim() : null;
            } else {
                log.debug("Selector '{}' did not match any elements", selector);
            }
        } catch (Exception e) {
            log.debug("Could not extract text with selector: {}", selector, e);
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
                        .setHeadless(false) // 默认设置为false，方便调试
                        .setSlowMo(100) // 减慢操作速度
                        .setArgs(List.of(
                            "--no-sandbox",
                            "--disable-setuid-sandbox",
                            "--disable-dev-shm-usage",
                            "--disable-accelerated-2d-canvas",
                            "--no-first-run",
                            "--no-zygote",
                            "--disable-gpu",
                            "--disable-web-security",
                            "--disable-features=IsolateOrigins,site-per-process",
                            "--enable-features=NetworkService,NetworkServiceInProcess"
                        )));

                // 创建浏览器上下文并配置反反爬虫设置
                context = browser.newContext(new Browser.NewContextOptions()
                        .setUserAgent(userAgent)
                        .setViewportSize(1920, 1080)
                        .setExtraHTTPHeaders(Map.of(
                            "Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8",
                            "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
                            "Accept-Encoding", "gzip, deflate, br",
                            "Referer", "https://www.zhipin.com/",
                            "DNT", "1",
                            "Connection", "keep-alive",
                            "Upgrade-Insecure-Requests", "1"
                        ))
                        .setGeolocation(31.2304, 121.4737) // 设置地理位置（上海）纬度、经度
                        .setLocale("zh-CN")
                        .setTimezoneId("Asia/Shanghai"));

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
                    // 添加更多浏览器指纹伪装
                    "Object.defineProperty(navigator, 'platform', {" +
                        "get: () => 'Win32'" +
                    "});" +
                    "Object.defineProperty(navigator, 'deviceMemory', {" +
                        "get: () => 8" +
                    "});" +
                    "Object.defineProperty(navigator, 'hardwareConcurrency', {" +
                        "get: () => 4" +
                    "});" +
                    "Object.defineProperty(navigator, 'cookieEnabled', {" +
                        "get: () => true" +
                    "});" +
                    "Object.defineProperty(navigator, 'doNotTrack', {" +
                        "get: () => null" +
                    "});" +
                    // 伪装canvas指纹
                    "const canvas = document.createElement('canvas');" +
                    "const ctx = canvas.getContext('2d');" +
                    "ctx.textBaseline = 'top';" +
                    "ctx.font = '14px Arial';" +
                    "ctx.fillText('test', 2, 2);" +
                    "const originalToDataURL = canvas.toDataURL;" +
                    "canvas.toDataURL = function(type, quality) {" +
                        "if (type === 'image/png') {" +
                            "return 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==';" +
                        "} else {" +
                            "return originalToDataURL.call(this, type, quality);" +
                        "}" +
                    "};" +
                "}");

                // 创建页面
                Page page = context.newPage();

                // 设置页面加载超时
                page.setDefaultTimeout(30000);
                page.setDefaultNavigationTimeout(30000);

                // 访问目标页面
                page.navigate(searchUrl);

                // 等待页面加载
                page.waitForLoadState();
                
                // 检查是否被重定向到安全检查页面
                if (page.url().contains("security-check")) {
                    log.warn("Detected security check page during search. Waiting for manual verification or trying to bypass...");
                    // 等待更长时间，希望安全检查能自动通过
                    randomWait(10000, 15000);
                    
                    // 检查是否仍然在安全检查页面
                    if (page.url().contains("security-check")) {
                        log.error("Still on security check page during search. Manual intervention may be required.");
                        // 尝试重新加载页面
                        page.reload();
                        page.waitForLoadState();
                        randomWait(5000, 10000);
                    }
                }

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
                ".job-card",
                "[ka*='search_list_job']",
                ".job-primary",
                ".job-item",
                "[data-lg-tj-id]",
                "[data-itemid]"
            };

            List<ElementHandle> jobElements = new ArrayList<>();

            for (String selector : selectors) {
                try {
                    log.debug("Trying selector: {}", selector);
                    jobElements = page.querySelectorAll(selector);
                    if (!jobElements.isEmpty()) {
                        log.info("Using selector {} found {} job elements", selector, jobElements.size());
                        break;
                    } else {
                        log.debug("Selector {} returned no elements", selector);
                    }
                } catch (Exception e) {
                    log.debug("Selector {} not found or caused exception: {}", selector, e.getMessage());
                }
            }

            // 如果仍然没有找到元素，记录页面信息用于调试
            if (jobElements.isEmpty()) {
                log.warn("No job elements found with any selector in search results");
                
                // 记录页面标题
                String title = page.title();
                log.info("Page title: {}", title);
                
                // 记录页面URL
                String url = page.url();
                log.info("Page URL: {}", url);
                
                // 检查是否是验证码页面
                if (title.contains("验证码") || title.contains("验证") || title.contains("captcha")) {
                    log.error("Encountered CAPTCHA page. This is likely due to anti-crawling measures.");
                }
                
                // 记录部分页面内容用于调试
                String content = page.content();
                log.debug("Page content length: {}", content.length());
                
                // 检查页面内容中的关键字
                boolean hasJobContent = content.contains("job") || content.contains("职位") || content.contains("招聘");
                boolean hasSalaryContent = content.contains("salary") || content.contains("薪资");
                boolean hasCompanyContent = content.contains("company") || content.contains("公司");
                boolean hasCaptcha = content.contains("captcha") || content.contains("验证码");
                
                log.info("Page content analysis - Jobs: {}, Salaries: {}, Companies: {}, CAPTCHA: {}", 
                    hasJobContent, hasSalaryContent, hasCompanyContent, hasCaptcha);
                
                // 截取部分内容记录日志（避免日志过大）
                if (content.length() > 5000) {
                    log.debug("Page content (first 5000 chars): {}", content.substring(0, 5000));
                } else {
                    log.debug("Page content: {}", content);
                }
                
                // 尝试查找页面上的所有元素，看是否存在职位相关的关键字
                try {
                    List<ElementHandle> allElements = page.querySelectorAll("*");
                    log.info("Total elements on page: {}", allElements.size());
                    
                    // 查找包含职位相关关键字的元素
                    int jobRelatedElements = 0;
                    for (ElementHandle element : allElements) {
                        try {
                            String innerText = element.innerText();
                            if (innerText != null && 
                                (innerText.contains("job") || 
                                 innerText.contains("职位") || 
                                 innerText.contains("招聘") || 
                                 innerText.contains("salary") ||
                                 innerText.contains("薪资") ||
                                 innerText.contains("company") ||
                                 innerText.contains("公司"))) {
                                jobRelatedElements++;
                                log.debug("Found job-related element with text: {}", 
                                    innerText.length() > 100 ? innerText.substring(0, 100) + "..." : innerText);
                            }
                        } catch (Exception e) {
                            // 忽略单个元素的异常
                        }
                    }
                    log.info("Found {} job-related elements on page", jobRelatedElements);
                    
                    // 如果找不到任何职位相关元素，可能是页面结构发生了变化
                    if (jobRelatedElements == 0) {
                        log.warn("No job-related elements found. The page structure may have changed or the search returned no results.");
                    }
                } catch (Exception e) {
                    log.warn("Error while analyzing all page elements", e);
                }
                
                return jobs;
            }

            // 处理找到的职位项
            for (int i = 0; i < Math.min(jobElements.size(), 20); i++) { // 限制处理数量
                ElementHandle jobElement = jobElements.get(i);
                log.debug("Processing job item {}/{} in search results", i+1, jobElements.size());
                try {
                    JobPosition jobPosition = new JobPosition();
                    jobPosition.setSource("zhipin");
                    jobPosition.setCrawlTime(LocalDateTime.now());

                    // 提取职位标题
                    log.debug("Extracting title for job item {}/{}", i+1, jobElements.size());
                    String title = extractTextWithSelector(jobElement, "[data-v-7a4b5b6e] .job-name");
                    if (title == null || title.isEmpty()) {
                        title = extractTextWithSelector(jobElement, ".job-title .job-name");
                    }
                    if (title == null || title.isEmpty()) {
                        title = extractTextWithSelector(jobElement, ".job-name");
                    }
                    jobPosition.setTitle(title);

                    // 提取公司名称
                    log.debug("Extracting company for job item {}/{}", i+1, jobElements.size());
                    String company = extractTextWithSelector(jobElement, "[data-v-7a4b5b6e] .company-name");
                    if (company == null || company.isEmpty()) {
                        company = extractTextWithSelector(jobElement, ".company-info .name");
                    }
                    if (company == null || company.isEmpty()) {
                        company = extractTextWithSelector(jobElement, "[class*='company-name']");
                    }
                    if (company == null || company.isEmpty()) {
                        company = extractTextWithSelector(jobElement, ".company-title");
                    }
                    if (company == null || company.isEmpty()) {
                        company = extractTextWithSelector(jobElement, ".company-name");
                    }
                    jobPosition.setCompany(company);
                    log.debug("Company name: {}", jobPosition.getCompany());

                    // 提取工作地点
                    log.debug("Extracting location for job item {}/{}", i+1, jobElements.size());
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
                    if (location == null || location.isEmpty()) {
                        location = extractTextWithSelector(jobElement, ".area");
                    }
                    if (location == null || location.isEmpty()) {
                        location = extractTextWithSelector(jobElement, ".job-area");
                    }
                    jobPosition.setLocation(location);
                    log.debug("Job location: {}", jobPosition.getLocation());

                    // 提取薪资范围
                    log.debug("Extracting salary for job item {}/{}", i+1, jobElements.size());
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
                    if (salary == null || salary.isEmpty()) {
                        salary = extractTextWithSelector(jobElement, ".salary-text");
                    }
                    if (salary == null || salary.isEmpty()) {
                        salary = extractTextWithSelector(jobElement, ".job-salary");
                    }
                    
                    // 解析薪资范围
                    parseSalaryRange(jobPosition, salary);
                    log.debug("Parsed salary range: {}-{}", jobPosition.getSalaryMin(), jobPosition.getSalaryMax());

                    // 提取职位描述
                    log.debug("Extracting description for job item {}/{}", i+1, jobElements.size());
                    String description = extractTextWithSelector(jobElement, "[data-v-7a4b5b6e] .job-detail");
                    if (description == null || description.isEmpty()) {
                        description = extractTextWithSelector(jobElement, ".job-detail");
                    }
                    if (description == null || description.isEmpty()) {
                        description = extractTextWithSelector(jobElement, ".job-description");
                    }
                    if (description == null || description.isEmpty()) {
                        description = extractTextWithSelector(jobElement, ".job-desc");
                    }
                    if (description == null || description.isEmpty()) {
                        description = extractTextWithSelector(jobElement, ".text-desc");
                    }
                    if (description == null || description.isEmpty()) {
                        description = extractTextWithSelector(jobElement, ".desc");
                    }
                    if (description == null || description.isEmpty()) {
                        description = extractTextWithSelector(jobElement, ".job-card-body");
                    }
                    if (description == null || description.isEmpty()) {
                        description = extractTextWithSelector(jobElement, ".job-card-wrapper .info-desc");
                    }
                    if (description == null || description.isEmpty()) {
                        description = extractTextWithSelector(jobElement, "[data-v-7a4b5b6e] .info-desc");
                    }
                    
                    // 如果仍然没有找到描述，尝试获取整个职位卡片的文本内容
                    if (description == null || description.isEmpty()) {
                        try {
                            description = jobElement.innerText();
                            log.debug("Using full job element text as description: {}", 
                                description != null ? (description.length() > 200 ? description.substring(0, 200) + "..." : description) : "null");
                        } catch (Exception e) {
                            log.warn("Failed to extract full job element text", e);
                        }
                    }
                    
                    if (description != null && !description.isEmpty()) {
                        // 验证描述内容不等于薪资信息
                        String salaryText1 = extractTextWithSelector(jobElement, "[data-v-7a4b5b6e] .job-salary");
                        String salaryText2 = extractTextWithSelector(jobElement, ".job-salary");
                        
                        boolean isDescriptionValid = true;
                        if (description.equals(salaryText1) || description.equals(salaryText2)) {
                            log.debug("Description is same as salary, ignoring");
                            isDescriptionValid = false;
                        }
                        
                        // 检查描述是否过短
                        if (isDescriptionValid && description.length() < 10) {
                            log.debug("Description is too short (less than 10 chars): {}", description);
                            isDescriptionValid = false;
                        }
                        
                        if (isDescriptionValid) {
                            jobPosition.setDescription(description);
                            log.debug("Job description set: {}", 
                                description.length() > 200 ? description.substring(0, 200) + "..." : description);
                        }
                    } else {
                        log.debug("No valid description found for job item");
                    }
                    log.debug("Final job description: {}", jobPosition.getDescription());

                    // 提取经验要求
                    log.debug("Extracting experience for job item {}/{}", i+1, jobElements.size());
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
                    log.debug("Full job position details - Title: {}, Company: {}, Location: {}, Salary: {}-{}, Description: {}, Experience: {}", 
                        jobPosition.getTitle(), 
                        jobPosition.getCompany(), 
                        jobPosition.getLocation(), 
                        jobPosition.getSalaryMin(), 
                        jobPosition.getSalaryMax(), 
                        jobPosition.getDescription(), 
                        jobPosition.getRequiredExperience());
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