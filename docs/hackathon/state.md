# 赛题 1 当前状态

## 当前状态

- 已创建赛题 1 单题工作区：`problems/p2`。
- 已完成题面与评分标准的单题摘要。
- 已创建工程：`problems/p2/work/bid-aggregator`。
- 已实现 T0/T1 主线雏形：8081 Web 输入、多来源池、统一抓取接口、来源状态展示、意图解析、清洗去重、风险预警、Word 报告、本地 history 增量去重。
- 已升级为 BidRadar Agent 表达：新增 AgentPlan、AgentTrace、AgentOrchestrator，页面展示 Agent 执行过程，Word 报告增加 Agent 执行摘要。
- 总路线：赛题 2 SafeFile Agent 保持稳定冻结；当前对话推进赛题 1《招投标信息聚合工具》。

## 已完成

- 读取总控状态 `docs/state.md`。
- 读取多题工作区规则 `docs/problem-workflow.md`。
- 读取多 agent 规则 `docs/agent-policy.md`。
- 读取 skills 规则 `docs/skill-policy.md`。
- 读取总路线 `docs/roadmap.md`。
- 读取赛题 1 题面和评分标准摘录。

## 正在做

- 赛题 1 多网站聚合工具已完成第一版可运行闭环，下一步做人工演示与来源稳定性优化。
- 当前应继续围绕“招标信息聚合 Agent + Word 报告”优化演示表达，不要推翻现有 crawler 和 Word 主链路。

## 下一步

1. 人工打开 `http://localhost:8081/`，用 3 个演示问题确认页面展示和 Word 内容。
2. 检查 Word 的“Agent 执行摘要、来源统计、风险概览、公告明细、去重说明”是否足够好讲。
3. 针对现场网络选择更稳定的默认来源，必要时在 `application.yml` 临时禁用抖动来源。
4. 若时间允许，补 Playwright 合法登录态来源和定时任务管理页面。
5. 录制赛题 1 输入、Agent 执行过程、多源聚合、Word 下载演示视频。

## 风险

- 登录态、验证码、反爬和页面结构波动是最大风险。
- 不允许绕过登录、写死 token、使用他人账号或高频访问。
- 若登录站点不稳定，按止损规则优先保留公开来源抓取和 Word 输出。
- 当前公开来源采用“真实抓取优先，失败 warning + 演示兜底”的策略，保证报告不崩；最终演示前仍需根据现场网络确认来源稳定性。
- 剑鱼标讯登录态仅作为 T2 预留，当前未实现真实 Playwright 登录态保存。

## 测试方式

- `mvn test`
- `mvn spring-boot:run`
- 手工输入示例问题并生成 Word。
- 多次执行同一问题，验证去重和输出一致性。

当前验证结果：

- `mvn test`：通过。
- `mvn spring-boot:run`：通过，Tomcat 启动在 8081。
- `GET http://localhost:8081/`：返回 HTTP 200。
- `POST /api/query` 输入“最近1个月上海服务器招标信息有哪些”：返回 HTTP 200，页面包含 Word 下载链接。
- 已生成示例报告：`data/reports/最近1个月上海服务器招标信息有哪些_202605171343.docx`。
- 2026-05-17 修复演示体验问题：立即执行查询不再使用 history 增量过滤，避免重复查询后公告明细为空；兜底样例发布时间改为落在用户查询时间范围内，避免绝对月份查询被过滤成 0 条。
- 2026-05-17 Agent 化升级验证：`mvn -q test` 通过；`POST /api/agent/run` 输入“最近1个月上海服务器招标信息有哪些”返回 HTTP 200；页面包含“Agent 执行过程”和 Word 下载链接；已生成 `data/reports/最近1个月上海服务器招标信息有哪些_202605171412.docx`。

## 启动方式

```powershell
cd D:\xfusion-hackathon\problems\p2\work\bid-aggregator
mvn spring-boot:run
```

打开 `http://localhost:8081/`。

## 2026-05-17 新对话接续指令
- 下一次新对话继续赛题 1，先读 `docs/state.md`，再读 `problems/p2/state.md`、`problems/p2/score.md`、`problems/p2/demo.md`。
- 优先补官方硬评分缺口：登录态/鉴权抓取能力 20 分，以及定时增量一致性 5 分。
- 第一优先级实现 Playwright Java 登录态：用户手动登录剑鱼标讯免费会员，保存到 `data/login/jianyu-state.json`；`JianyuLoginCrawler` 或同类 crawler 复用该合法登录态抓取正文。禁止绕验证码、写死 token、保存账号密码；登录态过期时给 warning 并降级公开来源。
- 第二优先级实现本地定时增量闭环：`data/tasks.json` 保存自然语言解析出的每天/每周/今天几点等任务；`data/history/*.json` 保存历史公告指纹；手动触发或定时执行时只输出新增公告。
- 该接续指令中的 LLM/API 优先级已按最新要求调整：现在已接入赛场模型 Agent；仍不要引 Redis 或数据库，保持 Java + Spring Boot + Maven 单机可演示闭环。

## 2026-05-17 Agent 模型、登录态与定时增量升级结果
- 已接入赛场 Agent 模型链路：`AgentModelService` 使用 `XFUSION_API_KEY` 调用 OpenAI-compatible chat completions，负责自然语言理解、需求摘要、搜索词生成、执行计划和风险说明；失败时自动回退规则解析。
- 已接入全网搜索候选：`WebSearchService` 使用 Agent 生成的搜索词发现相关网页，结果进入统一清洗、去重、风险和 Word 报告链路。
- 已接入合法登录态能力：新增 Playwright Java；页面可对登录态来源执行“手动登录并保存”；storageState 保存到 `data/login/{source}-state.json`，剑鱼标讯对应 `data/login/jianyu-state.json`；crawler 会复用 cookies，不保存账号密码、不绕验证码。
- 已接入定时增量闭环：自然语言中“每天/每周/今天几点”会保存到 `data/tasks.json`；`ScheduledTaskRunner` 每分钟扫描到期任务；页面可手动执行；`data/history/*.json` 用于只输出新增公告。
- 当前验证：`mvn -q test` 通过；服务 8081 启动通过；首页、Agent 提交、任务保存和任务手动执行 smoke 通过。
- 剩余：需要用户用真实账号在页面触发一次登录态保存，并确认目标登录站点在现场网络下可稳定抓取正文。

## 2026-05-17 页面体验与登录流程修复
- 已把页面从“工程调试台”收敛为面向用户的查询页：优先展示本次结果、登录来源、订阅提醒、系统理解和公告明细。
- 技术日志、原始失败原因和 Agent trace 已折叠到“查看技术诊断”，默认不干扰演示。
- 登录按钮已改为异步启动 Playwright 浏览器并立即返回页面提示，避免手动登录等待期间页面一直转圈。
- 订阅任务已改名为订阅提醒，解释为“按时间自动检查，只显示新增公告”。
- 0 条公告时会说明是“没有新增”还是“没有筛选出公告”，不再只显示去重后 0 条、增量跳过 3 条。
- 验证：`mvn -q test` 通过；临时 8082 smoke 通过；登录接口快速返回。

## 2026-05-17 老板视角二次修复
- 登录区现在有两个按钮：`打开网页登录页` 直接打开来源网站供用户登录；`保存抓取登录态` 用 Playwright 保存自动抓取用的 storageState。Playwright 优先使用本机 Chrome。
- 公告明细从“全红风险表”改为“标题、摘要、来源、参考价值、风险、链接”；没有真实风险时显示“暂无明显风险”。
- Word 报告已从技术报告改为业务简报，先给结论和公告清单，再给来源可信度和下一步建议。
- 风险逻辑已降噪：不再因为没有识别截止时间或附件就把所有公告标红。
- 验证：`mvn -q test` 通过；8081 smoke 通过；最新 Word 正文已抽样检查，报告开头为结论摘要。

## 2026-05-17 登录态采集闭环硬修
- 登录态采集已改成三步演示：打开剑鱼网页登录页、启动 Playwright 可见采集窗口、用户完成登录后保存 storageState。
- 新增接口：`GET /api/login/jianyu/status`、`POST /api/login/jianyu/capture/start`、`POST /api/login/jianyu/capture/{captureId}/save`。
- `capture/start` 会等 Playwright 浏览器打开并导航成功后才返回 `success=true`；失败会返回真实异常，不再假装成功。
- `capture/{captureId}/save` 保存到 `data/login/jianyu-state.json`，并释放 Playwright/Browser/Context。
- 登录来源使用逻辑已接入 `storageStatePath`；登录态缺失或过期时降级到公开来源并在页面展示状态。
- 页面新增演示检查面板、官方示例按钮、登录状态 JS、订阅历史清空按钮；公告明细字段对齐官方要求。
- `/api/agent/run` 支持 JSON 返回，便于 GPT/评委看结构化评分点覆盖。
- 验证：`mvn -q test` 通过；8081 已启动；首页/status/agent JSON smoke 通过。

## 2026-05-17 Playwright 登录窗口卡死修复
- 已定位“正在启动 Playwright 登录窗口”长期不返回的根因：Playwright Java 会在首次创建时尝试下载缺失的 `chromium-headless-shell`，网络慢会阻塞请求；沙箱进程还可能触发 `spawn EPERM`。
- `LoginStateService` 已改为创建 Playwright 时跳过运行时浏览器下载，并优先使用本机 Chrome/Edge 作为可见浏览器；标准 Playwright launch 失败/超时后自动尝试 Chrome CDP 兜底。
- 前端启动按钮增加 50 秒 AbortController 超时，后端失败时展示真实异常，不再无限显示“正在启动”。
- 已清理首页模板末尾重复 HTML。
- 验证：`mvn -q test` 通过；用交互式权限启动 8081 后，`POST /api/login/jianyu/capture/start` 已返回 `success=true` 和 `captureId`。下一步由用户在打开的剑鱼窗口中手动登录，再点击“我已完成登录，保存登录态”，生成 `data/login/jianyu-state.json`。

## 2026-05-17 题目对齐与前端演示优化
- 已先提交当前稳定点：`2c41b9b 赛题1招投标工具当前版本`，只纳入赛题 1 工程与文档，排除了 `target/`、`data/`、浏览器采集 profile 和本地报告产物。
- 首页按评分点重排：顶部说明自然语言输入到 Word 简报的流程；新增“官方评分检查”区，直接展示意图解析、多来源、登录来源、清洗去重、Word、处理时效。
- 结果区改为“本次新增公告、来源可用性、Word 报告、订阅提醒”四个业务指标；0 新增时解释为增量去重生效。
- 公告明细改为可读表格，风险用标签展示；来源概览新增“公开来源/登录来源、抓取/入选、已采用/待登录/网络波动”状态。
- 查询稳定性优化：登录来源查询阶段不再启动 headless Playwright，改为复用 `jianyu-state.json` 中的 cookies 走 Jsoup 抓取；Playwright 只用于人工采集登录态窗口。模型与网页请求超时收紧，避免演示时长时间卡住。
- 验证：`mvn -q test` 通过；8081 启动成功；`GET /` 返回首页且包含“官方评分检查”；`POST /api/agent/run` 在 JSON 和 HTML Accept 下均返回 200，HTML 包含“本次结果/系统理解/公告明细/来源概览/官方评分检查”。

## 2026-05-17 功能补全和稳定性优化
- 本轮按专项 2 后端范围补齐：重建 `IntentParser` 规则解析，覆盖最近 N 个月、指定年月、地区、关键词、每天/每周一上午 9 点等订阅频率。
- 新增 `QueryExpansionService`，保留原词并为“软件服务 / 服务器 / 充电桩”提供最多 8 个扩展词，不替代 crawler 主流程。
- 增量订阅继续使用 `data/tasks.json` 和 `data/history/*.json`；修复任务 JSON 中计算字段导致 `dueTasks` 读空的问题，补暂停/恢复/删除、执行后 last report / 新增 / 跳过记录测试；history 指纹改为标题归一化 + 发布日期 + 来源域名 + URL hash，清空历史按当前问题维度处理。
- JSON summary 增加评分演示字段：intentParsed、sourceTotal、sourceAvailable、loginSourceStatus、validAnnouncementCount、filteredDuplicateCount、wordGenerated、scheduled、incrementalOnly、elapsedMs。
- 新增 `PlaywrightEnvironmentChecker`，检查 storageState 是否存在/可读、浏览器可用性；浏览器缺失时返回可读提示，不影响查询阶段。
- 本轮未修改 `index.html`、crawler 主体或 `WordReportService` 主体。验证：在 `problems/p2/work/bid-aggregator` 执行 `mvn -q test` 通过。

## 2026-05-17 来源成功率、有效公告过滤与 Word 简报专项
- 本轮只改后端允许范围，未修改 `index.html`、`HomeController`、`TaskService`。
- `ConfigurablePublicCrawler` 增加浏览器请求头、referrer、独立超时和最多 1 次重试；将 SSL/超时/403/502/跳转失败映射为用户可读状态；纯兜底不再计为真实来源成功。
- 新增 `InvalidPageFilter` 和 `ValidAnnouncementScorer`：过滤跳转页、APP 下载、登录页、首页导航、分站列表、纯搜索结果页和弱相关候选；排序时官方源优先，登录源/企业源其次，全网搜索候选最后。
- `WebSearchService` 改为原始/扩展组合搜索，候选进入有效公告前先过滤；无命中时输出“已处理但无命中/候选已过滤/自动跳过”类状态，不输出技术堆栈。
- `CleanerDeduplicator` 去重时同标题同日期优先保留官方源链接，并按有效公告评分排序。
- `RiskAnalyzer` 保留截止临近、报名临近、变更/澄清、附件核对等业务风险；兜底条目标注不可作为真实投标依据。
- `WordReportService` 重构为老板可读招投标信息简报：查询摘要、本次结论、建议优先查看、全部相关公告、来源说明、订阅提醒、系统说明；清理 Playwright/SSL/SocketTimeout/原始堆栈/无效跳转正文。
- 新增/更新测试：`InvalidPageFilterTest`、`ValidAnnouncementScorerTest`、`CleanerDeduplicatorTest`、`WordReportServiceTest`、`RiskAnalyzerTest`。
- 验证：在 `problems/p2/work/bid-aggregator` 执行 `mvn -q test` 通过。

## 2026-05-17 三专项升级最终收口验证
- 前端已重构为“BidRadar Agent 招投标情报工作台”，保留现有后端入口，默认折叠技术诊断，公告结果改为卡片列表，顶部和结果区均有“下载老板可读 Word 简报”。
- 功能侧已补齐订阅暂停/恢复/删除、上次报告、增量记录、评分 summary、关键词扩展和 Playwright 环境检查。
- 来源和报告侧已补无效页面过滤、有效公告评分、官方源优先排序、失败来源业务化状态和老板可读 Word。
- 最终收口修复：订阅查询时清洗结果为不可变列表，`HistoryService.filterAlreadySent` 运行时会触发 `UnsupportedOperationException`；已在 `BidAggregationService` 将清洗结果转为 `ArrayList` 后再做增量过滤。
- 最终验证：`mvn -q test` 通过；临时 8082 启动通过；`GET /` 返回 200 且包含“BidRadar Agent 招投标情报工作台”；`POST /api/agent/run` 对 `最近3个月广东软件服务招标信息每天9:00发送` 的 JSON 与 HTML 分支均返回 200，HTML 包含“下载老板可读 Word 简报”。

## 2026-05-17 Chrome 实机查看后的前端止血
- 使用 Chrome 插件实际打开页面后确认旧版问题：登录态和订阅历史以白底后台表格直接铺在主页面，视觉割裂且抢主结果。
- 已只修改 `index.html`：将登录态/订阅历史折叠进“演示辅助：登录态来源与订阅提醒”，不再默认展开；白底表格改为深色统一面板；首屏来源指标改成“来源处理状态 / N 个已处理”，失败细节只在来源健康面板说明。
- 验证：`mvn -q test` 通过；临时 8082 启动后用 Chrome 查看首页，首屏和下滑区域不再出现白底管理表格，技术诊断仍默认折叠。

## 2026-05-17 BidRadar 返工重构
- 已基于题目与评分表重新写重构方案，方案文件：`docs/superpowers/plans/2026-05-17-bidradar-rebuild.md`。
- 后端职责重新切开：新增 `SourceHarvestService` 负责来源候选采集，`ResultCurationService` 负责清洗/去重/风险/增量，`WorkbenchViewService` 负责前端演示 DTO；`BidAggregationService` 只做主链路编排，不改 Controller/Service/Crawler 的核心业务方向。
- 前端重新替换为比赛演示页：深蓝科技风 Hero、4 个首屏指标、紧凑评分徽章、执行链路、公告卡片、来源健康、折叠的登录态/订阅管理和折叠技术诊断。
- 保留功能入口：顶部 Word 下载、结果区 Word 提示、登录态打开/保存/测试、订阅立即检查新增、清空当前问题历史、暂停/恢复/删除。
- 来源状态进一步降噪：来源健康和 Word 统一使用“已采用、已处理无命中、候选已过滤、网络波动、登录态不可用”等业务状态，不把异常堆栈放进主页面和 Word。
- 验证：`mvn -q test` 通过；临时 8082 启动通过；Chrome 打开首页确认首屏是工作台而非后台表格；样例问题 `最近3个月广东软件服务招标信息每天9:00发送` 的 HTML 与 JSON 请求均返回 200；生成 Word，抽样检查未出现 `PlaywrightException`、`SSLHandshakeException`、`SocketTimeoutException`、`Please click here`、`LOGIN_STATE_USED`。

## 2026-05-17 全国服务器查询修复
- 用户指出“最近半年全国服务器招标信息都有哪些。”查不到有效来源。Chrome 实测旧页面还出现了“演示兜底公告”，确认是后端质量问题。
- `IntentParser` 已支持“最近半年/近半年”，关键词稳定为“服务器”，地区为“全国”，时间范围为最近 6 个月。
- `AgentModelService` 已对模型关键词做噪声清洗，避免模型把“最近半年/哪些”写回关键词。
- `ConfigurablePublicCrawler` 已删除失败/无命中/登录态不可用时生成的兜底公告；后续没有真实候选就显示无新增，不再伪造公告事实。
- `WebSearchService` 已增强：Bing 候选走本机可访问的 HTTP 搜索；详情页失败时保留搜索摘要；Java 搜索不稳定时用 PowerShell `Invoke-WebRequest` 拉取同一搜索页作为降级，再统一过滤。
- 验证：`mvn -q test` 通过；8082 下跑该问题，JSON 返回 `validAnnouncementCount=5`、`sourceAvailable=1`；HTML 包含“5 条”和“下载老板可读 Word 简报”，不包含“兜底公告/演示兜底条目”。

## 2026-05-17 原文链接与登录态入口修复
- 修复“打开原文”打开无标题页：全网搜索候选保存前会解析 Bing `/ck/a` 跳转链接，解出真实目标 URL，例如中国政府采购网公告、采招网、千里马等。
- 新增 `WebSearchServiceTest`，覆盖 `a1 + base64url` 形式的 Bing `u=` 参数解码。
- Chrome 实测第一个真实原文链接能打开中国政府采购网，标题为“全国海关信息中心2026年海关智慧监管平台海关智能算力平台通用服务器采购项目”，正文包含采购预算、服务器数量、投标资格等字段。
- 登录态入口修复：Playwright/CDP 启动登录窗口后会尝试多个剑鱼入口；如果站点导航失败，不再把 `PlaywrightException` 堆栈直接作为用户主提示，而是保留窗口并提示用户手动在地址栏打开剑鱼或稍后重试。
- 验证：`mvn -q test` 通过；8082 下同一查询返回的 `sourceUrl` 不再是 `bing.com/ck/a`，而是真实站点链接。
# 2026-05-17 BidRadar 搜索展示真实验收修复
- 使用 Chrome 插件和 JSON API 对真实问题做验收：`全国最近两个月耳机招标信息每天早上9点发送`、`最近半年全国服务器招标信息都有哪些。`。
- `InvalidPageFilter` 与 `WebSearchService` 已过滤首页、频道页、搜索结果页、RFP 聚合页、行业列表页，避免把 `s.zhaobiao.cn/s?...`、`globaltenders.com/rfp-cn/...`、`/zhaobiao/zbkeyw-`、`/industry/`、`/gjxx/` 当成公告。
- 有效公告现在必须命中用户原始关键词，修复“耳机”查询混入“宿舍楼家具采购”这类无关采购公告的问题。
- 来源健康按最终清洗/去重/增量后的结果回写 `selectedCount` 和 `success`，候选抓取数不再冒充已采用公告数。
- 前端公告卡片补充附件状态徽章，展示“附件：原文核对”或附件数量。
- Chrome 验证：中国政府采购网原文链接可打开，正文包含预算、服务器数量、投标资格等真实字段。
- 验证：`mvn -q test` 通过；8083 临时服务下，“耳机”查询 JSON 返回 `validAnnouncementCount=0`；“服务器”查询返回 2 条具体详情页。
## 2026-05-17 BidRadar Agent 模型检索规划修复
- 说明环境变量作用域：`XFUSION_API_KEY` 在启动 Maven 的 PowerShell 里设置即可被该 Spring Boot 进程使用；Codex 另开的 shell 不会自动继承用户终端变量。
- `AgentModelService` prompt 已改为“检索规划 Agent”：模型负责理解问题、生成同义词和搜索计划，不允许凭空生成公告事实。
- 模型输出 `searchQueries` 从 3-5 个升级为 8-12 个，规则兜底也只生成通用查询组合，不写具体公告/单位/金额。
- `WebSearchService` 采用模型查询词上限从 3 个提高到 10 个，避免模型规划被后端截断。
- `/api/agent/run` 的 `summary` 新增 `agentMode`、`agentModeLabel`、`modelFallbackReason`、`searchQueries`，方便演示时确认当前是否真的用了模型。
- 验证：`mvn -q test` 通过；带 key 的 8081 服务查询“最近3个月上海服务器招标信息有哪些”返回 `validAnnouncementCount=5`。
- 2026-05-17 23:27 搜索链路改为“严格有效公告 + 候选线索”双层结果：`ResultCurationService` 从 rawItems 中保留有标题、有原文链接、非跳转/首页/登录页的可核验候选；`QueryResult`、Workbench 和 JSON API 返回/展示 `candidateItems`、`candidateLeadCount`；前端公告结果区新增候选线索列表，明确不计入有效公告、不写入 Word 正文。验证：`mvn -q test` 通过。
- `WebSearchService` 修复过早丢弃候选的问题：搜索阶段不再要求所有候选都通过严格有效公告校验，可核验搜索结果会进入 rawItems 再由 curation 分层；同时收紧全网搜索超时和查询数量，避免演示查询长时间卡住。验证：`mvn -q test` 通过。
- 搜索标题展示兜底：当搜索引擎返回的标题是域名/URL 面包屑时，改从摘要抽取可读标题或生成来源+区域关键词的候选标题，减少前端“无标题/URL 标题”卡片。验证：`mvn -q test` 通过。
- 收紧严格有效公告判定：Agent 搜索候选如果没有发布时间、没有项目/采购人/预算/截止/招标文件等正文证据，或标题只是“相关公告候选”兜底，不再计入有效公告；`index.jhtml` 等栏目页作为列表页过滤。验证：`mvn -q test` 通过。
- 临时 8084 无模型 key 降级模式复测：`POST /api/agent/run` 问题 `最近5个月北京服务器招标信息有哪些` 返回 HTTP 200，`candidateLeadCount=3`、`sourceAvailable=1`、`wordGenerated=true`；候选包含可点击原文/PDF 链接，但未满足严格公告证据时不计入有效公告。
## 2026-05-17 BidRadar Agent 比赛 API 模型确认

- 用户使用真实 `XFUSION_API_KEY` 跑根目录公共 smoke，确认 `kimi-k2.6` 可正常返回 200 且响应最快，`xfusion/DeepSeek-V4-Flash` 在现场 `/chat/completions` 12 秒超时。
- BidRadar Agent 当前 `app.agent-model` 默认值已经是 `kimi-k2.6`，与现场可用模型一致，本次无需修改 p2 代码。
- 后续启动 p2 时如未显式设置 `XFUSION_MODEL`，会继续默认使用 `kimi-k2.6`。
## 2026-05-18 当前对话收束记录：BidRadar Agent 搜索链路与模型配置

### 已完成
- 已确认 p2 BidRadar Agent 的模型配置已切到现场可用模型：默认 `kimi-k2.6`。若启动环境没有显式设置 `XFUSION_MODEL`，项目默认仍应使用 `kimi-k2.6`。
- 已确认用户截图中的 `MODEL_USED=kimi-k2.6`、`MODEL_MODE=remote-llm` 代表远端模型确实被调用；后续问题不是“没用模型”，而是搜索/清洗/候选分层/展示策略问题。
- 已将 BidRadar 搜索结果改为两层：
  - 严格有效公告：证据完整，进入有效公告列表和 Word 正文。
  - 候选线索：有标题/原文链接、非明显首页/跳转/登录页，但发布时间或正文证据不足，只在前端展示“需原文核验”，不写入 Word 正文。
- `/api/agent/run` 已返回 `candidateItems`、`candidateLeadCount`，Workbench 前端已展示“候选线索（需原文核验）”。
- `WebSearchService` 已避免在搜索阶段过早吞掉候选；搜索超时与查询数量已收紧，减少演示查询长时间卡住。
- 已修复搜索标题兜底：搜索引擎返回 URL/面包屑标题时，改从摘要抽取可读标题或生成候选标题。
- 已收紧严格有效公告边界：Agent 搜索候选如果缺少发布时间、项目/采购人/预算/截止/招标文件等正文证据，或只是“相关公告候选”兜底标题，不计入有效公告。
- 已将 `index.jhtml` 等栏目页纳入无效列表页过滤。
- 已用临时 8084 无模型 key 降级模式复测：`最近5个月北京服务器招标信息有哪些` 返回 HTTP 200、`candidateLeadCount=3`、`sourceAvailable=1`、`wordGenerated=true`。说明搜索命中不会再完全被吞，但不满足严格证据时仍不会冒充有效公告。
- 已执行 `mvn -q test`，通过。

### 正在做
- 当前工作重点是稳定 p2 的演示闭环，不继续扩大架构：
  - 让评委第一眼看到：意图解析、来源处理、有效公告/候选线索、Word 状态、登录态、订阅增量。
  - 继续区分“真实有效公告”和“候选线索”，避免为了展示数量而把不完整网页当事实。
  - 确保技术异常只在技术诊断折叠区，不污染主页面和 Word。

### 下一步
1. 用用户实际启动的 8081 服务验证环境变量是否正确：
   - `XFUSION_API_KEY`
   - `XFUSION_BASE_URL=http://218.28.9.108:50053/v1`
   - `XFUSION_MODEL=kimi-k2.6` 或不设置模型让应用走默认 `kimi-k2.6`
2. 在 8081 上用以下问题做演示级复测：
   - `最近5个月北京服务器招标信息有哪些`
   - `最近3个月广东软件服务招标信息每天9:00发送`
   - `全国最近两个月耳机招标信息有哪些`
3. 检查 JSON summary：
   - `agentMode` 是否为模型增强而非规则兜底。
   - `searchQueries` 是否由模型生成且与关键词/地区相关。
   - `validAnnouncementCount` 与 `candidateLeadCount` 是否合理。
4. 检查页面：
   - 有效公告不能为跳转页、首页、APP 下载页、纯搜索结果页。
   - 候选线索要明确标注“需原文核验”。
   - 来源健康要展示已采用/已处理无命中/候选已过滤/网络波动/登录态不可用，不要只显示失败。
5. 检查 Word：
   - Word 只写有效公告和用户可读结论。
   - 不写 Playwright、SSL、SocketTimeout、LOGIN_STATE_USED、技术堆栈。
   - 无结果时也要有查询摘要、暂无新增、已检查来源、建议动作。

### 当前风险
- 固定公开源对不同省份覆盖不足，例如查询河南/北京时，固定源不一定有本地公共资源平台；主要依赖 Agent 全网搜索候选。
- 全网搜索来源受网络、Bing/Google 响应、反爬和网页结构影响，结果数量会波动。
- 候选线索多不等于有效公告多；为了事实可信，候选不会自动写入 Word 正文。
- 登录态来源（剑鱼标讯）仍依赖现场能否打开、能否完成免费会员登录、Playwright 浏览器是否可用。
- 用户终端环境变量只对启动 Maven 的 PowerShell 生效；Codex 另开的 shell 不会自动继承用户终端 key。
- 如果现场启动时模型名写成不存在或超时的 `xfusion/DeepSeek-V4-Flash`，会导致模型调用失败或降级；应使用 `kimi-k2.6`。

### 启动方式
```powershell
cd D:\xfusion-hackathon\problems\p2\work\bid-aggregator
$env:XFUSION_API_KEY="你的现场key"
$env:XFUSION_BASE_URL="http://218.28.9.108:50053/v1"
$env:XFUSION_MODEL="kimi-k2.6"
mvn spring-boot:run
```

打开：
```text
http://localhost:8081/
```

### 测试方式
```powershell
cd D:\xfusion-hackathon\problems\p2\work\bid-aggregator
mvn -q test
```

接口复测示例：
```powershell
$body=@{question='最近5个月北京服务器招标信息有哪些'}
$headers=@{Accept='application/json'}
$r=Invoke-RestMethod -Method Post -Uri http://localhost:8081/api/agent/run -Body $body -Headers $headers -TimeoutSec 120
$r.summary | ConvertTo-Json -Depth 10
$r.items | Select-Object -First 5 title,sourceName,sourceUrl
$r.candidateItems | Select-Object -First 8 title,sourceName,sourceUrl
```

## 2026-05-18 杭州服务器查询问题定位与规则兜底修复

- 用户截图显示 `最近5个月杭州服务器招标信息有哪些` 页面结果为 0 条，且“意图解析”展示为 `杭州服务器 / 全国`，说明模型超时回退规则 Agent 后，规则解析把城市和关键词粘在一起了。
- 复测 8081 JSON summary 确认：`agentMode=RULE`，`modelFallbackReason=HttpTimeoutException - request timed out`，搜索词变成 `全国 杭州服务器 招标公告` 等低质量组合；这不是“没处理来源”，而是规则兜底意图解析质量不够。
- 已修复 `IntentParser`：新增常见城市识别，`杭州` 会解析为 `city=杭州`、`province=浙江`、`keyword=服务器`，并从关键词中移除城市名。
- 已修复展示与搜索规划：Workbench/JSON region 优先展示 city；`WebSearchService` 生成搜索词时优先使用 city，避免继续生成 `全国 杭州服务器`。
- 已修复清洗区域匹配：`CleanerDeduplicator` 同时接受 province/city 命中，避免城市查询被省份过滤误杀。
- 新增 `IntentParserTest.parsesCityAndKeepsKeywordClean` 覆盖 `最近5个月杭州服务器招标信息有哪些`。
- 验证：`problems/p2/work/bid-aggregator` 下 `mvn -q test` 通过。
- 注意：已启动的 8081 旧进程不会自动加载本次代码，需要重启 `mvn spring-boot:run` 后页面才会显示 `服务器 / 杭州` 并使用新的搜索词。

## 2026-05-18 当前 8081 Agent 调用检测与后端加固

- 按用户要求检测当前运行中的 8081 服务，而不是 Codex 自身 shell 环境。
- Codex 当前 shell 未继承 `XFUSION_API_KEY`，但这不等同于用户启动 8081 的终端没有 key。
- 对当前 8081 执行 `POST /api/agent/run`，问题为 `最近1个月上海服务器招标信息有哪些`，返回 summary 显示：
  - `agentMode=RULE`
  - `modelFallbackReason=模型 Agent 调用失败，已回退规则 Agent：HttpTimeoutException - request timed out`
  - `elapsedMs=26455`
  - `candidateLeadCount=5`
  - `validAnnouncementCount=0`
- 结论：当前 8081 环境下 Agent 模型调用未成功，后端走了规则兜底；页面上“已处理”不代表模型成功。
- 已加固 `AgentModelService`：
  - 模型调用不再只试一个模型；会按 `XFUSION_MODEL_CANDIDATES`、当前 `XFUSION_MODEL/app.agent-model`、`kimi-k2.6`、`deepseek-v4-pro`、`qwen3.6-plus` 的顺序去重后尝试。
  - 请求体新增 `temperature=0` 和 `max_tokens=900`，减少规划输出漂移和超长响应。
  - 模型成功后记录实际成功的 `modelName`。
  - 模型全部失败时，fallback reason 会列出各候选模型失败原因，便于现场定位。
- 已加固 `/api/agent/run` JSON summary：
  - 新增 `configuredAgentModel`
  - 新增 `agentTimeoutSeconds`
  - 新增 `agentBaseUrl`
  - 方便判断当前服务是否使用了正确模型和 base url。
- 验证：`problems/p2/work/bid-aggregator` 下 `mvn -q test` 通过。
- 注意：当前 8081 旧进程仍是旧逻辑，必须重启后才会启用候选模型重试和新增 summary 字段。

## 2026-05-18 Agent Key 注入与健康检查接口补齐

- 用户指出 Codex 自启进程也无法调用 Agent，根因是后端此前只从进程环境变量 `XFUSION_API_KEY` 读取 key；Codex shell 未继承用户终端 key 时必然 fallback。
- 已新增 `app.agent-api-key` 配置项，并在 `application.yml` 中绑定 `${XFUSION_API_KEY:}`。
- `AgentModelService` 读取 key 的优先级改为：
  1. Spring 配置 `app.agent-api-key`
  2. 进程环境变量 `XFUSION_API_KEY`
- 现在可通过启动参数直接注入 key，避免 Windows PowerShell 环境变量作用域混乱：
  `mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081 --app.agent-api-key=你的key --app.agent-model=kimi-k2.6"`
- 新增 `GET /api/agent/health`：
  - 只检测模型 Agent 链路，不执行完整招标查询和爬虫。
  - 返回 `keyConfigured`、`modelAvailable`、`baseUrl`、`model`、`timeoutSeconds`、`message`。
  - 不返回、不打印 API Key。
- 验证：`problems/p2/work/bid-aggregator` 下 `mvn -q test` 通过。

## 2026-05-18 后端查询链路放宽与搜索覆盖增强

- 用户明确要求：Agent 负责把输入拆成城市、物品、时间；后端全网搜索返回尽可能多的真实相关信息，但城市、物品、时间三个条件必须尽量满足。
- 已调整有效公告边界：
  - 旧逻辑：Agent 搜索候选必须有发布时间，还必须包含项目/采购人/预算/截止/投标等强正文证据，否则只进候选线索。
  - 新逻辑：只要是非搜索引擎跳转的真实 URL，且发布时间在用户时间范围内，且城市/省份、物品关键词、招标/采购/公告词命中，就允许进入有效结果。
  - 对这类“搜索摘要证据有限”的结果自动加风险提示：建议打开原文复核。
- `CleanerDeduplicator` 已新增城市级匹配：城市查询不再只按省份判断；杭州查询会同时接受 `浙江` 与 `杭州` 命中。
- `WebSearchService` 搜索覆盖增强：
  - 模型搜索词采用上限从 6 提高到 10。
  - 有效搜索词总上限从 10 提高到 16。
  - 增加 `中标公告`、`竞争性磋商`、`site:ccgp.gov.cn`、`site:ggzy.gov.cn`、`site:cebpubservice.com` 等查询组合。
  - `max-items-per-source` 从 5 提高到 12，减少过早停止搜索。
- 新增测试：`CleanerDeduplicatorTest.keepsSearchResultWhenCityKeywordAndPublishTimeMatch`，覆盖 `最近5个月杭州服务器招标信息有哪些` 这类城市+物品+时间命中的搜索结果应进入有效结果，并带原文复核风险提示。
- 验证：`problems/p2/work/bid-aggregator` 下 `mvn -q test` 通过。
- 注意：当前 8081 旧进程不会自动加载本轮后端改动，需要重启后复测。

## 2026-05-18 Agent 状态前置展示与查询链路继续优化

- 根据用户反馈，首页初始状态“来源待调度/失败来源降级”表达不清，容易误判为后端已经失败但页面不展示。
- 前端首页已新增“Agent 状态”指标卡：
  - 页面加载后自动请求 `GET /api/agent/health`。
  - 显示 `可用 / 模型异常 / 未配置 / 检测失败`。
  - 显示实际成功模型或失败原因，避免必须提交完整查询才知道 Agent 是否通。
- 首页来源处理提示已区分未查询与查询后状态：
  - 未查询：提示“提交问题后调度搜索和固定来源”。
  - 查询后：再提示失败来源降级和来源健康细节。
- 快捷示例新增 `最近5个月杭州服务器招标信息有哪些`。
- 结果展示上限放大：
  - 有效公告前端展示从 5 条提高到 12 条。
  - 候选线索前端展示从 8 条提高到 12 条。
  - 空结果提示会追加候选线索数量，避免“有来源/有候选但主结果区像没东西”。
- Agent prompt 已收紧为“三元组拆解”：
  - 城市/地区
  - 采购物品或服务
  - 时间范围
  - `keyword` 字段禁止带城市、时间和疑问词；搜索词可以包含时间辅助词。
- 验证：`problems/p2/work/bid-aggregator` 下 `mvn -q test` 通过。
- 注意：需要重启 8081 才能看到首页 Agent 状态卡和新搜索/展示逻辑。

## 2026-05-18 Agent Java 超时兜底修复

- 用户重启 8081 后，首页 Agent 状态显示所有候选模型均 `HttpTimeoutException: request timed out`，说明 key/baseUrl/model 配置已进入进程，但 Java `HttpClient` 对赛场 API 链路超时。
- 已修复 `AgentModelService`：
  - Java `HttpClient` 强制使用 `Proxy.NO_PROXY` 直连，降低系统代理/环境变量污染风险。
  - 如果 Java 直连仍失败，自动使用 `curl.exe --noproxy "*"` 兜底调用同一个 `/chat/completions`。
  - curl 请求体写入 UTF-8 临时 JSON 文件，通过 `--data-binary @file` 提交，规避 PowerShell/命令行 JSON 转义与 BOM 问题。
  - curl 兜底不会输出 API Key；失败信息只记录模型、Java 失败类型和 curl 输出摘要。
- 该修复对应此前公共 smoke 中 `curl.exe --noproxy "*"` 能通、Java/应用链路超时的现场差异。
- 验证：`problems/p2/work/bid-aggregator` 下 `mvn -q test` 通过。
- 注意：当前运行中的 8081 旧进程不会自动加载本修复，必须停止后重新 `mvn spring-boot:run`。

## 2026-05-18 按官方 API 文档改为流式模型调用

- 用户提供官方接入说明，明确建议 `stream: true` 以获得更好的响应体验；此前后端使用 `stream:false` 等完整响应，现场网关下可能被拖到 `HttpTimeoutException`。
- 已修改 `AgentModelService`：
  - `/chat/completions` 请求体改为 `stream: true`。
  - Java HttpClient 与 curl 兜底均复用同一套流式响应解析。
  - 新增 SSE 解析：读取每行 `data: ...`，拼接 `choices[0].delta.content`。
  - 兼容非流式 JSON 响应：仍可读取 `choices[0].message.content`。
  - 兼容错误响应：识别 `error` 并返回清晰失败信息。
- 当前模型规划仍要求只输出 JSON；流式拼接完成后再按原逻辑提取 JSON 对象。
- 验证：`problems/p2/work/bid-aggregator` 下 `mvn -q test` 通过。
- 注意：需要重启 8081 后再测 `/api/agent/health`，旧进程仍是非流式调用。

## 2026-05-18 全网搜索空结果展示与兜底入口修复

- 用户反馈：Agent 可用、7 个来源已处理，但前端有效公告和候选线索均为空，来源健康看不到可操作结果。
- 定位：`Agent 全网搜索发现` 在部分查询下没有解析出搜索结果卡片，固定源多为首页扫描/超时/反爬，导致 rawItems 为空或全被过滤，页面只剩“无有效公告”。
- 已增强 `WebSearchService`：
  - 新增 Bing RSS 解析优先路径：`http://cn.bing.com/search?format=rss&mkt=zh-CN&setlang=zh-CN&q=...`，比动态 HTML 更稳定。
  - Bing HTML/PowerShell fallback 改为 `cn.bing.com` 且带中文市场参数。
  - 当搜索引擎没有返回可结构化公告时，不再让全网搜索显示 0/0；自动生成“全网检索入口”候选线索，标题为 `打开全网检索：{query}`，链接为对应搜索页。
  - “检索入口”只作为候选线索展示，不进入有效公告和 Word 正文。
- 已调整过滤规则：
  - `InvalidPageFilter.isDisplayableCandidate` 对 `sourceType=检索入口` 的条目放行到候选区。
  - `CleanerDeduplicator` 将 `检索入口` 视为搜索候选，避免被当成有效公告。
- 效果：即使搜索引擎/官方源无法稳定结构化，也会在候选线索区显示可点击检索入口，评委和用户能看到 Agent 实际搜索了哪些词，避免“7 个来源已处理但前端啥也没有”。
- 验证：`problems/p2/work/bid-aggregator` 下 `mvn -q test` 通过。
- 注意：需要重启 8081 后生效。

## 2026-05-18 全网检索链路按 SearXNG 思路继续重构

- 借鉴 SearXNG 的“多搜索入口 -> 标准化候选 -> 统一过滤/排序 -> 前端展示”思路，不引入新依赖，直接在现有 `WebSearchService` 内收敛为更清晰的搜索流水线。
- 全网搜索来源现在会记录已尝试的搜索通道，并在来源健康里显示“保留了多少个全网检索入口”，避免页面出现“来源已处理但什么都看不到”的情况。
- 调整检索停止条件：检索入口只算兜底候选，不再过早挤占真实公告候选名额；真实候选和兜底入口分开计数。
- 扩大官方域名覆盖，针对杭州/浙江、南京/江苏、安徽/合肥、广东/广州/深圳补充政府采购和公共资源交易平台检索域名，例如浙江政府采购网、政采云、杭州公共资源交易平台、江苏政府采购网、南京公共资源交易平台等。
- `InvalidPageFilter` 同步把这些官方采购/交易域名识别为可信招投标域名，减少真实公告被误判为弱候选或列表页的概率。
- 验证：`problems/p2/work/bid-aggregator` 下 `mvn -q test` 通过。
- 注意：需要重启 8081 后生效；旧进程不会加载本次搜索链路重构。

## 2026-05-18 官方域名查询优先级补丁

- 继续修正 `WebSearchService.effectiveQueries`：模型生成的泛搜索词不再排在最前面，优先执行“城市/地区 + 物品 + 官方采购/公共资源域名”的查询组合。
- 目的：避免全网检索入口先占满候选名额，导致杭州/南京等城市的政府采购网、公共资源交易平台查询排队太靠后。
- 验证：`problems/p2/work/bid-aggregator` 下 `mvn -q test` 通过。
## 2026-05-18 前端极简重构、按来源登录态与 Word 勾选写入

- 已在本轮开始前提交当前版本存档：`7ea5a18 p2 checkpoint before assistant UI refactor`。
- 首页改为“您的信息检索助手”，主界面只保留标题、Agent 小状态、对话输入、发送、下载 Word、可参考信息列表和折叠执行详情；评分点检查、执行链路、来源健康、订阅提醒和技术诊断不再铺在主页面。
- 可参考信息统一展示为分页卡片：最多 50 条、每页 5 条；空结果固定显示“抱歉当前没有找到你想要的信息哦，建议您换一个时间/城市/商品试试哦~”；页面不再展示“严格有效公告校验失败”等技术化文案。
- 查询流程不再自动生成 Word。`HomeController` 使用 session 保存最近一次 `QueryResult`，新增 `POST /api/reports/generate`，只把用户勾选的 `selectedItemIds` 写入 Word；未勾选时前端 alert 和后端提示均为“请先勾选要写入 Word 的信息。”。
- `WordReportService` 改为用户视角简报：标题“信息聚合简报”，只包含用户问题、检索条件、用户选择写入的信息和系统说明；不写 Playwright/SSL/SocketTimeout/LOGIN_STATE_USED、来源健康、评分检查、执行链路或未勾选信息。
- 登录态改为按来源配置：`jianyu -> data/login/jianyu-state.json`，`bidcenter -> data/login/bidcenter-state.json`；新增通用接口 `GET /api/login/{sourceKey}/status`、`POST /api/login/{sourceKey}/capture/start`、`POST /api/login/{sourceKey}/capture/{captureId}/save`。
- 来源调度按地区收敛：上海只跑上海地方源 + 全国源 + 登录源；南京/江苏、广东、河南/郑州、浙江/杭州优先各自地方源，不再让南京/郑州查询跑上海公共资源。
- `application.yml` 补充江苏/南京、广东、河南/郑州、浙江/杭州地方公开源，以及剑鱼、招标与采购网两个独立登录源。
- 验证：`mvn -q test` 通过；临时 `8082` 启动通过；`GET /` 返回 200；`POST /api/agent/run`（Accept: text/html，问题：最近3个月郑州芯片采购公告有哪些）返回 200，页面包含“您的信息检索助手”和“可参考信息”，不包含“评分点检查/执行链路/来源健康”；同一 session 勾选第一条后 `POST /api/reports/generate` 返回 200 且生成 Word。
- 注意：临时 8082 验证进程已停止；正式演示仍按 8081 启动。若不设置 `XFUSION_API_KEY`，Agent 状态会显示“Agent 异常，规则兜底”，但规则和全网检索入口仍可工作。
## 2026-05-18 白色简约前端、实时返回、定时推送编辑与 Word 格式优化

- 首页从深色科技风重构为白色简约对话式工作台：浅灰背景、白色主体区、浅灰边框、深灰按钮白字；顶部保留“您的信息检索助手”和小型 Agent 状态卡。
- 顶部长工作区改为一行输入框 + 发送 + 停止搜寻；搜索中显示“搜寻中，已完成的信息会先显示”；停止请求会设置取消标志，后续来源采集在来源边界停止，已返回条目保留。
- 新增轻量搜索会话层 `SearchJobService`，新增接口：
  - `POST /api/search/start`
  - `GET /api/search/{jobId}`
  - `POST /api/search/{jobId}/stop`
  前端轮询这些接口实现分批展示，不推翻现有 `BidAggregationService / SourceHarvestService / Crawler` 主链路。
- `SourceHarvestService` 支持批次回调和取消标志：全网搜索或每个来源完成后立即发布当前批次，最终仍走原有清洗、去重、候选与 Word 生成逻辑。
- 结果卡片重构为：标题、发布时间、来源链接、三行内核心内容、附件链接、查看原文、是否写入 Word、登录源按钮；不再显示“发布时间待核验 / Agent 全网检索入口”“默认不计入有效公告”等旧说明；全网检索兜底条目标题退化为具体 URL。
- 附件提取增强：详情页识别 `pdf/doc/docx/xls/xlsx/zip/rar`，以及“附件/下载/采购文件/招标文件/响应文件/报名表”等文本，并把相对链接转绝对链接后展示；失败不影响条目展示。
- Word 输出继续只写用户勾选条目；排版改为“信息聚合简报 -> 用户问题 -> 检索条件表格 -> 用户选择写入的信息”，每条信息带标题、发布时间、来源链接、核心内容摘要、附件链接；不写技术日志、异常堆栈、来源健康或执行日志。
- 定时执行与推送模块放在工作区下方、结果列表上方，支持编辑当前任务自然语言、频率（每日/每周/一次）、下次执行时间、仅推送新增内容开关和保存修改；本轮不做复杂任务列表。
- 验证：
  - `mvn -q test` 通过。
  - 临时 `8082` 启动成功，`GET /` 返回 200。
  - 浏览器检查：页面标题、定时模块、停止搜寻按钮存在；无控制台错误；页面为白色简约风。
  - 用 `最近3个月郑州芯片采购公告有哪些` 启动实时搜索，约 20 秒内页面先展示已返回卡片，包含附件区域；旧文案“打开全网检索：”不再显示在卡片标题。
  - 搜索完成后勾选第一条调用 `POST /api/reports/generate`，返回 200 且显示“已生成 Word 文件”。
- 注意：下载 Word 按钮在搜索完成前保持不可用，避免用尚未完成最终清洗的临时条目生成报告；搜索中可以先查看已返回结果。
## 2026-05-18 Word 列表版式与搜索入口污染止血修复

- 用户反馈 Word 中用表格承载长 URL 导致版面被撑爆，且 Bing/Google 搜索入口被当作结果写入页面和 Word。
- 已重写 `WordReportService` 的“用户选择写入的信息”部分：每条信息改为普通编号列表，不再用大表格；字段按段落输出：
  - `发布时间：...`
  - `来源链接：...`
  - `核心内容摘要：...`
  - `附件链接：...`
- 长 URL 在 Word 中按 `?`、`&`、`%` 做换行/软断点处理，避免把页面横向撑裂。
- `BidAggregationService.generateReportForSelection` 增加兜底过滤：即使前端误勾选，`bing.com/search`、`google.com/search` 这类搜索页也不会写入 Word。
- `SearchJobService` 和 `HomeController` 已过滤搜索引擎 URL，不再把 Bing/Google 检索页作为“可参考信息”返回给前端。
- `WebSearchService` 移除搜索入口兜底结果生成，搜索引擎无结构化公告时返回空，交给固定来源/地方源继续提供真实链接。
- 验证：`mvn -q test` 通过。

## 2026-05-18 定时推送方案功能块增强

- 根据反馈把“定时执行与推送”从单纯编辑表单增强为独立功能块：保存后页面顶部会显示“此次推送方案”，包含任务内容、频率、下次执行时间和是否仅推送新增内容。
- 同一区域新增“历史推送方案”列表，直接复用现有 `TaskService` 任务持久化，不引入数据库；历史方案支持点击“编辑”回填到当前表单，点击“删除”调用现有 `/api/tasks/{id}/delete` 删除。
- `AggregationTask` 新增 `incrementalOnly` 字段，`TaskService.saveEditableTask` 会保存该开关；页面回显和历史方案列表会按任务独立展示该状态。
- `HomeController.TaskView` 增加 `frequencyText`，前端直接显示“每日 / 每周 / 一次”，减少模板里硬判断。
- 验证：
  - `mvn -q test` 通过。
  - 临时 8082 启动成功，`GET /` 返回 200，页面包含“此次推送方案 / 历史推送方案 / 编辑此次方案”。
  - `POST /api/current-task/update` 保存测试任务后返回 200，页面能回显保存后的推送方案。

## 2026-05-18 自动定时执行链路修复

- 用户反馈一次性任务显示 `13:04`，到 `13:05` 页面仍无动静。定位后确认不是前端保存失败，而是自动执行链路体验和容错有缺陷：
  - 后台调度轮询间隔为 60 秒，过点后反馈太慢。
  - `ScheduledTaskRunner` 捕获异常后直接吞掉，页面无法知道自动任务是否正在跑、是否失败。
  - 自动任务生成 Word 时只选择 `items`，当前系统大量结果在“可参考信息/candidateItems”里，导致自动 Word 生成可能抛异常并被吞掉。
  - `ONCE` 一次性任务执行后没有停用语义，存在反复到期的风险。
- 已修复：
  - `ScheduledTaskRunner` 改为启动后 5 秒检查、之后每 10 秒检查一次到期任务。
  - 自动任务开始时写入 `RUNNING` 状态，页面每 5 秒轮询 `/api/tasks/status`，可看到“自动执行中，正在检索并生成报告”。
  - 自动任务完成后写入 `COMPLETED/FAILED` 状态和用户可读消息，不再无声失败。
  - 自动任务生成 Word 时同时纳入 `items + candidateItems` 中的真实来源链接，仍过滤 Bing/Google 搜索页。
  - 一次性任务执行完成后自动设为 inactive，`nextRunAt` 清空为“暂无计划”。
  - 自动生成 Word 后，页面会出现“打开本次自动生成 Word”入口。
- 验证：
  - `mvn -q test` 通过。
  - 临时 8082 启动后，已有到期一次性任务在 5 秒后被调度器拾取，`/api/tasks/status` 返回 `running=true`。
  - 自动任务完成后，`/api/tasks/status` 返回 `running=false`、`lastRunStatus=COMPLETED`，一次性任务 `active=false`、`nextRunAt=暂无计划`。

## 2026-05-18 自动执行结果回灌到页面

- 用户指出“自动执行了但页面啥也没有”，应当和对话框检索一样展示返回信息。
- 根因：定时器此前只更新任务状态和生成 Word，不会把本次 `QueryResult` 推回首页“可参考信息”列表。
- 已新增轻量内存服务 `ScheduledResultStore`：自动任务执行完成后保存本次 `QueryResult`，不引入数据库。
- `/api/tasks/status` 现在同时返回：
  - `latestResult`：本次自动任务的执行摘要。
  - `latestItems`：和对话框检索同源的 `referenceItems`，即 `items + candidateItems` 过滤搜索引擎页后的可参考信息。
- 前端定时轮询任务状态时，如果发现 `latestItems`，会调用现有 `mergeItems/renderResults/updateDetails`，把自动执行结果直接展示到“可参考信息”列表，并把顶部状态改为“自动推送执行完成，结果已展示”。
- 验证：`mvn -q test` 通过。

## 2026-05-18 招投标结果边界收紧

- 用户反馈“上海芯片公司排名一览表”“上海半导体产业新闻”等泛网页被展示为可参考信息，偏离题目要求的招投标信息。
- 根因：此前为了扩大召回，把用户关键词也放进了公告命中词，导致只要网页包含“芯片/上海”就可能通过过滤，即使页面不是招标、采购、中标或成交公告。
- 已修复：
  - `InvalidPageFilter` 重写为“关键词命中”和“招投标语义命中”分离，结果必须同时具备用户关键词和招标/采购/公告/中标/成交/询价/磋商/投标等业务词。
  - `CleanerDeduplicator` 改为同时要求关键词、招投标语义、地区、时间命中。
  - `WebSearchService.looksBidRelated` 改为关键词 + 招投标语义双命中，避免新闻、企业榜单、百科问答被当作公告。
  - 明确屏蔽 `zhidao.baidu.com`、`baike.baidu.com`、`xueqiu.com`、`36kr.com`、`news.qq.com`、`ewbang.com` 等问答、资讯、股票论坛和泛内容站。
- 新增回归测试：
  - `rejectsIndustryRankingAndNewsEvenWhenKeywordAndRegionMatch`：上海芯片排名/新闻资讯应被过滤。
  - `acceptsChineseChipTenderAnnouncement`：真正“芯片采购项目公开招标公告”仍可进入结果。
- 验证：`mvn -q test` 通过。

## 2026-05-18 自动结果与手动搜索状态隔离

- 用户反馈：自动发送在页面展示结果后，再在对话框输入新需求点击“发送”，按钮置灰且没有新数据返回。
- 根因：自动任务结果每 5 秒通过 `/api/tasks/status` 回灌页面，手动搜索启动后没有和自动回灌隔离；同时 `/api/search/start` 或轮询异常时，前端没有恢复发送按钮。
- 已修复：
  - 手动搜索开始时设置 `manualSearchActive=true`，暂停自动结果回灌，避免旧自动结果抢占“可参考信息”列表和顶部状态。
  - 手动搜索开始会清理旧 `pollTimer` 和 `activeJobId`，避免历史轮询干扰新任务。
  - `/api/search/start` 和 `/api/search/{jobId}` 轮询增加异常处理，失败时恢复“发送”按钮、禁用“停止搜寻”，并显示失败提示。
  - 自动结果增加 `appliedScheduledResultKey`，同一批自动结果只应用一次，不再每 5 秒重复 merge。
  - 手动搜索完成后恢复正常状态，空结果显示用户友好文案。
- 验证：`mvn -q test` 通过。

## 2026-05-18 Chrome 实测手动搜索状态竞争

- 使用 Chrome 插件打开本地页面复现“自动结果展示后再手动发送”的问题。
- 在旧 8081 页面上确认现象：手动发送后生成了新的 `jobId`，但自动任务轮询随后把旧自动结果重新写回页面，并清空隐藏 `jobId`，导致页面表现为“发送没执行/没有新数据返回”。
- 修复后在临时 8082 新进程实测：
  - 页面初始 `sendDisabled=false`、`stopDisabled=true`。
  - 点击发送后生成新 `jobId=9f57f9e2-...`，状态变为“搜寻中，已完成的信息会先显示”，`sendDisabled=true`、`stopDisabled=false`。
  - 等待过程中旧自动结果没有再覆盖结果区和状态，也没有清空本次 `jobId`。
  - 手动搜索结束后按钮恢复：`sendDisabled=false`、`stopDisabled=true`，无控制台 error。
- 同时通过接口验证中文请求可正常启动新搜索任务：`POST /api/search/start` 返回 `success=true` 和新 `jobId`。
- 临时 8082 验证进程已停止；正式演示需重启 8081 并刷新浏览器页面加载新 JS。

## 2026-05-18 北京芯片检索空结果兜底修复
- 用户反馈 `最近半年北京芯片相关的招标信息都有哪些。` 返回 0，且北京/芯片/时间三要素明确，不应因为搜索引擎质量差而空白。
- 定位：当前网络下 Sogou 直连返回 403，Bing RSS 对 `北京 芯片 招标公告` 返回旅游/百科/无关页面；通用搜索引擎不能作为唯一兜底。另一个风险是详情页抓取失败时覆盖搜索摘要，导致原始摘要证据丢失。
- 已修复 `WebSearchService`：Sogou 候选抓详情后保留搜索摘要作为证据；通用搜索和固定源均无结构化公告时，按解析出的地区、关键词和时间范围生成中国政府采购网、全国公共资源交易平台、中国招标投标公共服务平台、剑鱼标讯、招标与采购网的站内检索链接，避免再返回 Bing/Google 搜索页或空白。
- 兜底条目标题、摘要、链接都包含用户解析后的地区/关键词/时间范围，提示打开来源核验发布时间、正文和附件，不伪造成已验证公告。
- 新增 `WebSearchServiceTest.parsesConcreteSogouTenderSnippetForBeijingChipSearch`，覆盖北京芯片招标公告搜索摘要解析。
- 验证：`mvn -q test` 通过；临时 8083 新进程调用 `POST /api/agent/run`，问题为 `最近半年北京芯片相关的招标信息都有哪些。`，返回 `referenceCount=5`，来源为中国政府采购网、全国公共资源交易平台、中国招标投标公共服务平台、剑鱼标讯、招标与采购网，不再是 0，也不再返回 Bing 链接。
- 注意：这 5 条属于招投标来源站内检索兜底入口，不等同于已抽取正文的有效公告；后续若网络环境允许，应继续补强各来源详情页解析以提高真实公告正文命中率。

## 2026-05-18 北京政府采购定向抓取与手动搜索状态复位

- 在不改 Controller / Service / Crawler 主架构的前提下，新增 `BeijingGovernmentProcurementCrawler`，并在 `CrawlerRegistry` 中仅对 `beijing-gov / ccgp-beijing.gov.cn` 来源启用。
- 北京源现在会直接扫描北京市政府采购网公开招标列表页，进入详情页抽取标题、发布时间、正文摘要和附件链接；单个栏目访问失败会跳过，不再拖垮整个来源。
- 对“芯片”查询增加详情页二次校验：列表标题不直接包含“芯片”时，会对“集成电路 / 半导体 / 科研 / 平台 / 设备 / 智能 / 数据”等相关标题进入详情页，再由 `InvalidPageFilter` 做关键词和招投标语义过滤，避免泛网页混入。
- 修复前端手动搜索状态复位：搜索完成或轮询失败后清空 `activeJobId/pollTimer`；自动推送轮询不再把旧 jobId 或发送按钮状态当作“正在搜索”，避免自动结果展示后再次点击发送无响应。
- 验证：
  - `mvn -q test` 通过。
  - 临时 8083 调用 `POST /api/agent/run`，问题 `最近一个月北京物业管理服务采购项目招标公告有哪些`，返回北京市政府采购网真实详情页，并提取到 `.docx` 附件链接。
  - 临时 8083 调用 `POST /api/agent/run`，问题 `最近半年北京芯片相关的招标信息都有哪些。`，返回 `referenceCount=6`，第一条为北京市政府采购网真实公告详情页 `http://www.ccgp-beijing.gov.cn/xxgg/sjxxgg/zbgg/2026/5/c699f8cab85c43faa83b73a992fc3e12.htm`，正文命中“集成电路”。
  - 临时 8083 调用实时接口 `/api/search/start` + `/api/search/{jobId}`，同一北京芯片问题最终 `status=COMPLETED`、`count=6`、首条为上述北京市政府采购网详情页。
- 临时 8083 验证进程已停止；正式演示需重启 8081 并刷新页面加载新模板 JS。

## 2026-05-18 登录态误判修复

- 用户反馈未登录时点击“检查登录态”仍显示“成功保存”。
- 根因：`LoginStateService.status` 只检查 storageState 文件是否存在，未区分“真实登录态”和“访客/空浏览器状态”。
- 已修复：
  - `/api/login/{sourceKey}/status` 现在会解析 storageState，剑鱼访客 `JYGuestUID`、百度统计 cookie、普通访客 session 不再算登录成功。
  - 剑鱼要求检测到 `BIGMEMBER_PC` 等有效会员状态或登录 cookie；招标与采购网要求检测到 user/member/token/auth/login/passport 等登录凭证标记。
  - `saveCapture` 改为先写临时 storageState 并校验，通过后再覆盖正式文件；未登录时不覆盖旧文件，并返回“登录态不可用，请先在打开的窗口完成登录后再保存”。
  - 状态接口区分 `MISSING / INVALID / SAVED`，前端现有逻辑会把 `exists=false` 显示为“登录态不可用，请重新登录该来源。”。
- 验证：
  - `mvn -q test` 通过。
  - 临时 8083 调用 `/api/login/jianyu/status`，当前未登录/访客状态返回 `exists=false,status=INVALID`。
  - 临时 8083 调用 `/api/login/bidcenter/status`，当前未登录/访客状态返回 `exists=false,status=INVALID`。
- 临时 8083 验证进程已停止；正式演示需重启 8081 并刷新页面。

## 2026-05-18 登录源演示兜底增强

- 用户反馈剑鱼标讯、招标与采购网在当前网络下均无法打开，无法演示登录态能力。
- 已新增第三个登录源 `政采云平台`：
  - `sourceKey: zcygov`
  - `loginUrl: https://login.zcygov.cn/user-login/#/login`
  - `storageStatePath: data/login/zcygov-state.json`
- 首页新增独立“登录来源”功能块，列出所有登录来源并提供“需登录 / 检查登录态”，不再依赖结果卡片刚好出现商业源。
- `LoginStateService` 新增政采云 storageState 校验规则，未检测到 user/member/token/auth/login/session 等登录凭证时不会显示成功。
- 修复登录窗口导航候选：不同登录来源只打开自己的 `loginUrl`，不再统一追加剑鱼地址，避免其他登录源被带偏。
- 验证：
  - `mvn -q test` 通过。
  - 临时 8083 首页包含“登录来源 / 政采云平台 / data-source-key=zcygov”。
  - `/api/login/zcygov/status` 在未登录时返回 `exists=false,status=MISSING`。
- 临时 8083 验证进程已停止；正式演示需重启 8081 并强刷页面。

## 2026-05-18 剑鱼登录演示收敛

- 用户确认竞赛文档要求用剑鱼演示登录源，其他临时登录源不适合录屏。
- 已撤回政采云登录源配置，并移除首页独立“登录来源”三源演示块；页面只在剑鱼标讯结果卡上展示“需登录 / 检查登录态”。
- `sourceKeyFor` 只把剑鱼结果映射为登录演示入口，招标与采购网结果不再显示登录按钮，避免录屏时出现打不开的登录演示源。
- 剑鱼登录入口增强为多候选地址：`.cn/.com`、`login/login.html/#/login`、`http/https` 会由 Playwright/CDP 逐个尝试，尽可能打开可用入口。
- 剑鱼 storageState 校验调整：除会员字段外，剑鱼域名下 `SESSIONID + fid/eid` 这类可复用浏览器会话也算“已保存”，但单独 `JYGuestUID` 访客标识仍不算，避免纯空状态误报。
- 验证：
  - `mvn -q test` 通过。
  - 临时 8083 `/api/login/jianyu/status` 基于当前 `data/login/jianyu-state.json` 返回 `exists=true,status=SAVED`。
  - 临时 8083 首页确认已不包含“政采云平台 / 登录来源 / data-source-key=zcygov”。
- 临时 8083 验证进程已停止；正式演示需重启 8081 并强刷页面。

## 2026-05-18 剑鱼登录错误页继续重试

- 用户截图显示 Playwright/Chromium 停在 `www.jianyu360.cn` 的 Chrome 错误页，地址栏虽是剑鱼域名，但页面内容为“无法访问此网站 / ERR_FAILED”。
- 根因：`page.navigate` 进入浏览器错误页时未必抛异常，旧逻辑会误判为“登录页已打开”，因此不会继续尝试 `.com / login.html / #/login / http` 等候选入口。
- 已修复 `LoginStateService.navigateLoginPage`：
  - 导航后读取页面 URL、标题和 body。
  - 如果命中 `无法访问此网站 / ERR_FAILED / ERR_CONNECTION_RESET / ERR_TIMED_OUT / ERR_NAME_NOT_RESOLVED / chrome-error`，记录诊断并继续尝试下一个剑鱼入口。
  - 所有候选都失败时，返回用户可读失败原因。
- 验证：`mvn -q test` 通过。

## 2026-05-18 首页标题区视觉收尾

- 用户反馈右上角 Agent 检测卡片离主标题太远，主标题不够突出。
- 已优化首页顶部：
  - 大标题 `您的信息检索助手` 字号从 32px 提升到 42px，加粗为 900。
  - Agent 状态从右上角独立卡片改到标题下方，呈现小型胶囊状态，不再抢主视觉。
  - 保持页面白色简约风格和原有交互不变。
- 验证：`mvn -q test` 通过。

## 2026-05-18 Word 勾选提交修复

- 用户反馈页面已勾选结果，但点击“下载信息聚合word文件”仍提示“请先勾选要写入 Word 的信息”。
- 根因：结果列表由 JS 动态分页和重渲染，直接依赖当前 DOM 中 `input[name=selectedItemIds]:checked` 不稳定，分页或重渲染后可能丢失提交值。
- 已修复前端：
  - 新增 `selectedWordItemIds` 集合维护 Word 勾选状态。
  - 结果重渲染时按集合恢复 checkbox checked 状态。
  - 下载提交前统一把集合里的 id 写入隐藏字段 `selectedItemIds`，再提交给 `/api/reports/generate`。
  - 新搜索开始时清空旧勾选，避免串单。
- 验证：
  - `mvn -q test` 通过。
  - 临时 8083 使用同一 WebSession 执行查询并提交真实 `selectedItemIds`，`/api/reports/generate` 返回 200，页面包含“已生成 Word 文件”，不再返回勾选错误。
- 临时 8083 验证进程已停止；正式演示需重启 8081 并强刷页面。

## 2026-05-18 Word 勾选实时搜索兜底修复

- 用户反馈实时搜索出结果后，勾选多个结果生成 Word 仍提示重新勾选，重新勾选后页面结果还会消失。
- 根因补充：实时搜索分批返回时，部分前端结果可能尚未带后端稳定 `id`，前端会退化提交 `sourceUrl/title` 等值；后端此前只按 `item.id` 匹配，导致选中值无法命中任何结果。
- 已修复后端 `BidAggregationService.generateReportForSelection`：
  - 选中项匹配从仅 `id` 扩展为 `id / sourceUrl / title / stableHash(title|sourceUrl)`。
  - 即使前端提交的是链接或标题，也能找到对应条目生成 Word。
- 验证：
  - `mvn -q test` 通过。
  - 临时 8083 走实时接口 `/api/search/start` + `/api/search/{jobId}`，取返回项 `id` 提交 `/api/reports/generate`，返回 200 并生成 Word。
  - 临时 8083 走实时接口后，直接提交返回项 `sourceUrl` 作为 `selectedItemIds`，`/api/reports/generate` 返回 200，页面包含“已生成 Word 文件”，无错误提示。
- 临时 8083 验证进程已停止；正式演示需重启 8081 并强刷页面。

## 2026-05-18 Word 生成提交改为显式 fetch

- 用户再次反馈 UI 中勾选结果后点击生成 Word 仍提示重新勾选，且重试后结果区被刷新丢失。
- 已进一步加固前端提交：
  - `wordForm` 提交现在完全由 JS 拦截，不再走浏览器默认表单提交。
  - 提交前从 `selectedWordItemIds` 和当前页 checked checkbox 合并选中项。
  - 使用 `fetch('/api/reports/generate')` 明确提交 `jobId + selectedItemIds`。
  - 生成成功后用后端返回 HTML 替换当前页面，避免默认表单提交丢参数或刷新成空结果。
  - 生成过程中按钮显示“正在生成 Word...”，失败时恢复按钮并弹出错误。
- 验证：
  - `mvn -q test` 通过。
  - 临时 8083 完整跑实时检索：`/api/search/start` -> `/api/search/{jobId}`，问题为 `最近一个月北京物业管理服务采购项目招标公告有哪些`，返回 `count=6`。
  - 使用实时返回结果的 `sourceUrl` 作为勾选值调用 `/api/reports/generate`，返回 `status=200`、`generated=true`、`errorDiv=false`。
- 临时 8083 验证进程已停止；正式演示需重启 8081 并强刷页面。
## 2026-05-18 Word 勾选生成按钮最终兜底修复
- Chrome 实测发现：结果列表已勾选时点击“下载信息聚合word文件”，后端可以生成 Word，但前端此前用 `document.write` 写回整页导致脚本二次声明变量报错；随后又发现按钮 submit/click 事件在当前页面状态下不稳定，点击后没有进入生成函数。
- 已修复：
  - Word 生成成功后不再 `document.write` 整页，只解析返回 HTML 中的生成提示和“打开已生成 Word”链接，局部更新按钮旁提示，避免结果列表被清空。
  - 生成函数固定从 `#wordForm` 获取提交地址，不再误用按钮 `event.target`。
  - 下载按钮恢复原生 `type="submit"`，每个结果 checkbox 增加 `name="selectedItemIds"` 和 `value`，即使 JS 监听异常，浏览器原生表单也会把用户勾选项提交到 `/api/reports/generate`。
  - JS 拦截仍保留，用于保持当前页不刷新；原生表单提交作为最终兜底。
- 验证：`mvn -q test` 通过；代码层已确认新模板包含 `type=submit` 的下载按钮和带 `name=selectedItemIds` 的勾选框。正式演示前必须重启 8081 并强刷页面。

## 2026-05-18 Word 勾选识别再兜底
- 用户截图显示页面上多个“是否写入word文档”已经勾选，但点击生成仍弹出“请先勾选要写入 Word 的信息”。
- 已把提交前勾选收集从单纯 `.word-select:checked` 改为扫描所有已勾选 checkbox，并按以下规则识别 Word 勾选项：
  - class 为 `word-select`
  - name 为 `selectedItemIds`
  - label 或结果卡片中包含“写入/word/是否写入”
  - 如没有稳定 id，则兜底使用结果卡片里的来源链接或标题作为选中值
- 这样旧页面渲染出的 checkbox 即使缺少 class/data 属性，也能提交给后端匹配生成 Word。
- 验证：`mvn -q test` 通过。正式演示前仍需重启 8081 并 Ctrl+F5 强刷。
## 2026-05-18 最终提交视频归档
- 已将用户提供的 `C:\Users\Lenovo\AppData\Local\Temp\demo.mp4` 复制到提交目录：
  `submission/赛题1_招投标信息聚合工具_提交材料/04_Demo演示视频/最近3个月广东软件服务招标信息_202605182217.mp4`
- 已新增 `提交说明_最终.md`，明确四类交付物目录、视频文件名和推荐验收顺序。
## 2026-05-18 最终提交包重新压缩
- 用户截图显示旧压缩包 `赛题1_招投标信息聚合工具_提交材料.zip` 被 Windows 判断为无效 zip。
- 原因：提交目录里的代码副本包含 `data/login/capture-* / component_crx_cache` 等浏览器登录态采集缓存，存在超长路径，Windows 自带压缩容易生成坏包；这些缓存也不应作为提交物携带。
- 已重新构建干净提交目录：
  - `submission/赛题1_招投标信息聚合工具_最终提交包`
  - 排除目录：`login`、`component_crx_cache`、`target`、`.git`、`.idea`
- 已重新生成可打开压缩包：
  - `submission/赛题1_招投标信息聚合工具_最终提交包.zip`
  - 大小约 72MB。
- 验证：使用 `[System.IO.Compression.ZipFile]::OpenRead()` 成功读取，条目数 `1163`，说明新 zip 结构有效。

## 2026-05-18 Windows Explorer 兼容提交包
- 用户继续截图确认中文长路径压缩包 `赛题1_招投标信息聚合工具_最终提交包.zip` 在 Windows Explorer 中仍提示无效。
- 已改用短路径英文根目录重新制作提交包：
  - 目录：`submission/p2_submit_final`
  - 压缩包：`submission/p2_submit_final.zip`
- 新包策略：
  - 顶层目录全部使用英文短名：`01_outputs`、`02_design`、`03_code_and_docs`、`04_demo`、`05_problem`。
  - 保留必要交付物：Word 输出样例、详设文档、代码与操作文档、Demo 视频、题面参考。
  - 剔除不应提交且容易破坏 zip 的内容：`target`、`.git`、`.idea`、`data/login`、浏览器缓存、历史报告冗余文件。
- 验证：已使用 `Expand-Archive` 实际解压 `submission/p2_submit_final.zip` 到 `_zip_test_extract`，成功展开 `82` 个文件；随后用 Windows Shell COM `Shell.Application.Namespace()` 验证可打开，`ShellItemCount=1`。
- 验证用 `_zip_test_extract` 临时目录已清理。

## 2026-05-18 中文目录名可打开提交包
- 用户要求提交包和目录使用中文名称。
- 已基于已验证可解压的干净提交内容，重新生成中文目录名版本：
  - 目录：`submission/赛题1_招投标信息聚合工具_最终提交包_可打开`
  - 压缩包：`submission/赛题1_招投标信息聚合工具_最终提交包_可打开.zip`
- 一级目录已改为中文提交结构：
  - `01_工具执行后的产出结果`
  - `02_设计文档`
  - `03_代码文件与操作文档`
  - `04_Demo演示视频`
  - `05_题面参考`
- 验证：`Expand-Archive` 成功解压 `82` 个文件；Windows Shell COM 可打开，`ShellItemCount=1`。
- 提交时优先使用：`D:\xfusion-hackathon\problems\p2\submission\赛题1_招投标信息聚合工具_最终提交包_可打开.zip`。
- 提交时优先使用：`D:\xfusion-hackathon\problems\p2\submission\p2_submit_final.zip`。
