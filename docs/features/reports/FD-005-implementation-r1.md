# FD-005 JS/TS 客户端实施报告 R1

<!-- aiw-data: FD-005-implementation-r1.json -->

- Worker Session：`fd005-host-20261016-93d8ca8e`；来源事件：`FD-005-000003-design-ready`。
- 设计/差异基线 `78f426d`，原业务基线 `cd31a46`；代码至 `a673ec0`。隔离记录核对：main / feature/FD-005 / D:/03_projects/pg-eventbus/.wt/FD-005。
- 用户已明确回复“两种都提供”，完成DB及Gateway包，未把未回复当作审批。

完成1.1—1.5和静态发现新增子项1.1.1/1.2.1；每项独立提交。两个独立ESM包位于client/javascript，提供直接JS import、TypeScript声明及真实消费示例，无生成构建物或兄弟包依赖，无npm安装/发布。两包helper有意自包含，本FD由1.1单一owner维护相同精度/验证逻辑。

| 验收 | 源码与实际证据 |
|---|---|
| AC-01 | 两包package.json导出JS/types、package files含代码/声明/示例/tsconfig，无依赖/replace；Node>=22.12。JS node --check、TS5.9.3 noEmit严格检查通过 |
| AC-02 | 两类均9操作，与Gateway路由、store和SQL逐项对照。DB参数绑定、先acquire锁再poll，锁失败不查事件；commit保留false，不释放/重试；future=6、cleanup=10000，maintenance等待SELECT完成 |
| AC-03 | id()拒绝不安全number/超int64，统一bigint；encode()用rawJSON保留JSON数字；decode()从源token恢复不安全整数；DB的id/payload显式text，UTC to_char保留微秒；Date/ISO输入校验、字符串保留纳秒 |
| AC-04 | HTTP校验URL/字段、消费者按路径段编码，拒绝dot段防fetch规范化；timer/signal覆盖fetch及body，finally清理，错误不回显原响应/网络异常；DB双引号转义schema、数据绑定，宿主query receiver保留，不关闭连接 |
| AC-05 | .d.ts/JS参数和结果静态逐项核对；两包checkJs及TS消费示例通过。总/包README、客户端/根README和当前指南/模型/索引更新，说明bigint、现代JSON、CORS、query_timeout及宿主责任、未发布 |
| AC-06 | 仅本机离线语法/类型及静态检查；当前没有独立Reviewer结果，未预先声称通过/交付。Gateway、CLI、SQL、旧语言客户端及历史归档未修改 |

实际命令及结果：

- `node --version`：v24.14.0；`node C:/green/aiw/http-openai-proxy/node_modules/typescript/lib/tsc.js --version`：5.9.3，均使用本机已有工具，无下载。
- db及gateway各执行一次 `node --check index.js`、`node --check codec.js`，退出0；各执行一次 `node <local-tsc> -p tsconfig.json --noEmit`，退出0，包含strict checkJs、声明和TS消费示例，不执行示例。
- 首次检查后静态确认fetch对`.`/`..`路径规范化的影响，新增HTTP拒绝分支及文档、包清单，提交a673ec0后仅重做受影响gateway的 `node --check .../index.js` 和 `tsc -p .../gateway/tsconfig.json --noEmit`，退出0；没有重复DB检查。
- `git diff --check`、`git diff --check 78f426d`通过；静态读取Gateway api/store与SQL，核对9操作/字段/锁/维护/日期精度。早期两次FD apply_patch因模板尾部定位和重复目标写法失败，随后以匹配当前模板的单次更新完成，无不完整变更；常规CRLF规范化警告未造成diff whitespace失败。

未运行测试、HTTP/DB/应用示例、npm install/pack/publish、额外驱动/编译器下载、formatter/linter、最终构建、浏览器/跨平台执行、推送或部署。无持续blocker，无技能/模板变更需要；现代JSON及fetch路径语义作为本次设计/文档约束记录。

剩余风险：未实际验证Node/浏览器HTTP互通、node-postgres连接/并发或SQL运行效果；类型检查不能证明运行语义。JSON精确支持取决于宿主现代运行时，泛型payload只作静态提示；小数为JS number。HTTP自定义fetch必须遵守AbortSignal，认证头和结构化details是宿主可信数据。DB query_timeout不等于服务端取消，外层事务改变可见性/租约；连接生命周期和错误脱敏由宿主负责。无自动续租/释放/调度，SQL既有清理风险仍在。
