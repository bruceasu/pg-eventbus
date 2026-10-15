# 将来接入 Flyway 的处理方式

当前项目只有按顺序执行的初始化 SQL，未启用 Flyway，也没有 flyway_schema_history。`docs/sql/` 是可修改的当前定义来源，`cli/sql/init.sql` 是生成物。三位编号只表示安装顺序，下面是未来接入设计，不是本次已经实现的功能或可直接用于生产的配置。

## 安装顺序与迁移版本

Flyway 的 `V001__description.sql` 表示只应用一次的唯一迁移版本，按版本数字排序，历史表记录校验和。已部署的版本文件应冻结，后续变更新增 V002、V003，不能覆盖旧文件或通过随意 repair 来掩盖变更。[Versioned migrations 官方说明](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/versioned-migrations)

可以在接入时建立独立迁移目录，例如：

```text
db/migrations/
  V001__initial_schema.sql
  V002__add_retry_index.sql
  R__010_consumer_functions.sql
  R__020_event_functions.sql
```

V001 是接入时审核并冻结的完整初始快照，包含建表、索引、当时函数与授权。当前 22 个安装片段不应简单地变成 22 个版本：它们主要是同一次初始化的功能拆分，没有过去的升级历史。

R__ 文件没有版本，内容校验和改变时重新应用；在所有待执行 V 之后执行，并按描述的字母顺序排序，故示例将三位数字放在 R__ 后。适合可安全 CREATE OR REPLACE 的函数定义。[Repeatable migrations 官方说明](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/repeatable-migrations)

不强制每个函数改动都用 R__。改变参数/返回类型、需要 DROP、依赖调整或数据修复时，通常应使用新的 V 迁移，显式处理兼容性与依赖。选择 R__ 后，其内容成为该函数的唯一可编辑定义；V001 中保留的是不可变历史快照，不能继续同时编辑 docs/sql 的副本。接入 FD 需要决定来源切换或导出方式，避免出现第二套活跃定义。

## 新数据库

1. 固定工具版本、迁移位置、目标数据库/schema、执行账号与应用角色。
2. 准备并审核冻结的 V001，以及真正的后续 V / 可选 R 文件；用同一套迁移文件覆盖所有环境，schema/角色作为受控部署参数。
3. 在可丢弃 PostgreSQL 数据库中验证从空库 migrate、重复 migrate、失败事务、函数调用和应用角色权限。
4. 使用 Flyway migrate 创建对象与版本历史。新库不应先执行当前 CLI db init 再盲目 baseline。

## 已由当前 CLI 初始化的数据库

先盘点实际对象、函数定义、索引、所有者、授权与默认权限，并与拟定 V001 比较；运行期生成的月份分区和业务数据需要单独识别，不应因快照比对而删除。不同部署若有差异，先形成明确的对齐方案，不能用同一个版本号掩盖差异。

确认数据库已代表 V001 对应状态后，才在明确指定的目标上执行 baselineVersion=1 的 **baseline**，随后 validate / migrate。baseline 是登记现有起点，跳过小于等于该版本的 V 文件，不会执行 V001、更不会自动证明现有结构与 V001 一致。[Baseline 官方说明](https://documentation.red-gate.com/flyway/reference/commands/baseline)

以下仅表达先后关系；连接、schema 与迁移位置须在接入时按所选工具版本配置，不能直接使用本项目 CLI 的 TOML 当作 Flyway 配置：

```text
flyway info
flyway -baselineVersion=1 baseline
flyway validate
flyway migrate
```

validate 的历史/校验和核对不能替代前面的实际对象盘点。baseline 后第一次执行的 R__ 仍可能替换已有函数，必须纳入审核与演练。默认不打开 baselineOnMigrate：它可对无历史的非空 schema 自动建立基线，降低连错目标时的保护，接入阶段应使用审计后的显式 baseline。[baselineOnMigrate 官方说明](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-baseline-on-migrate-setting)

## 本项目需要适配的细节

- **schema 和搜索路径**：当前源 bootstrap 默认 bus，生成的 CLI 模板含 `{{schema_ident}}`；它不是 Flyway 占位符，不能直接交给 Flyway。接入时建立专用、受控的渲染/配置规则，标识符与字符串分别转义，不能将任意输入直接替换进 SQL。
- **函数安全路径**：只配置 Flyway 的目标 schema 不够；在创建函数前明确设置包含目标 schema、末尾 pg_temp 的受控搜索路径，保持当前 SET search_path FROM CURRENT 的捕获语义。各迁移不能假定继承上一文件的会话状态。
- **事务边界**：当前 CLI 生成物有 BEGIN/COMMIT；Flyway 迁移应按工具事务管理方式重新组织，不直接套用 CLI 外围事务。PostgreSQL 非事务操作需单独规划，不混进初始事务脚本。
- **角色和权限**：应用角色提前存在；DDL 账号与应用账号分开。保留 schema USAGE、函数 EXECUTE 和默认权限策略，并核对 ALTER DEFAULT PRIVILEGES 对实际函数创建者生效。R__ 授权文件若内容未变，不会因为别的 R__ 新建函数而自动再执行；新增函数依靠正确默认权限或明确的授权变更处理，不能只靠文件排在最后。
- **CLI 的职责**：Flyway 接管数据库生命周期后，必须取消或限制 CLI db init 在受管库中的使用，防止绕过历史表重建函数/授权。事件与维护命令仍可调用数据库函数；不要让两套工具同时拥有 schema 升级职责。
- **失败与恢复**：备份与恢复方案、失败事务、数据库版本差异和 validate/checksum 异常必须在独立接入 FD 中处理。先调查实际状态；不要把 clean、盲目删除历史或随意 repair 当作常规恢复方式。

## 接入时需要交付的内容

单独创建 Flyway 接入 FD，明确工具/运行方式、唯一迁移来源、生成关系、全环境 schema 与账号、已有库对齐及 baseline 审计记录、升级/失败恢复演练、CLI 职责切换和操作文档。本次只落实安装编号与生成工具，没有运行 Flyway、baseline 或任何数据库命令。
