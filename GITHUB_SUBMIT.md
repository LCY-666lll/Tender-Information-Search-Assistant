# 赛题1 GitHub 仓库交接清单

## 仓库建议

建议单独新建仓库用于赛题1《招投标信息聚合工具》，仓库根目录使用：

```text
problems/p2/work/bid-aggregator
```

`problems/p2/problem.md`、`score.md`、`demo.md`、`state.md` 可作为仓库外说明，或复制到仓库 `docs/hackathon/`。不要把 p1、p3、根目录视频和提交压缩包带入。

## 必须提交

- `pom.xml`
- `README.md`
- `.gitignore`
- `sources.yml`
- `src/main/**`
- `src/test/**`
- `docs/**`

这些文件构成完整 Java / Spring Boot / Maven 工程，包含自然语言解析、多来源抓取、登录态采集入口、定时增量、Word 报告和测试。

## 不要提交

- `.idea/`
- `target/`
- `data/`
- `data/login/**`
- `data/reports/**`
- `data/history/**`
- `data/tasks.json`
- `*.log`
- `*.tmp`
- `submission/`

原因：`data/login` 可能包含本机浏览器 profile、cookies/storageState 和个人会话；`data/reports/history/tasks` 是现场运行产物；`submission` 是平台提交包，不适合作为源码仓库内容。

## 启动

```powershell
cd problems/p2/work/bid-aggregator
mvn spring-boot:run
```

打开：

```text
http://localhost:8081/
```

## 测试

```powershell
cd problems/p2/work/bid-aggregator
mvn -q test
```

当前最后记录：`mvn -q test` 通过。

## 环境变量

如需模型 Agent：

```powershell
$env:XFUSION_API_KEY="你的key"
```

不要把 key 写入 `application.yml`、README、提交说明或 Git 历史。

## 仓库完整性检查

新仓库中执行：

```powershell
git status --short
mvn -q test
```

确认没有 p1/p3 路径、没有 `data/login`、没有浏览器缓存、没有视频/zip。

## 剩余风险

- 登录态/鉴权抓取依赖现场网络、目标站点和免费会员登录状态；登录失败时应降级到公开来源。
- 多来源抓取受网站结构和反爬策略影响，演示前需预跑稳定问题。
