# Foodtrace 溯源合约

食品溯源系统的合约部分，跑在 FISCO BCOS 联盟链上，solc 0.8.11。

链上逻辑：监管机构给基地、加工、质检、运输、仓储、零售六类机构发角色，
产品由基地注册后沿环节流转，质检必须过，质检不合格或监管介入都会进入召回终态。
角色、产品、记录全部存链上。这个目录只有合约和它的测试，不负责部署。

## 目录

- `contracts/` 合约源码
- `test/` 测试（node:test，TypeChain 生成类型）
- `types/`、`artifacts/`、`cache/` 是编译生成物，不入库

## 测试

需要 Node.js 18 以上：

    npm install
    npm run compile
    npm test

47 个用例，正常应该全过。
