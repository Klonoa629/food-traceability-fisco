import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { network } from "hardhat";
import type { ContractTransactionResponse } from "ethers";
import type { Foodtrace } from "../types/Foodtrace.js";

const { ethers } = await network.create();

/* 测试常量 */
// 角色编号（与合约Role枚举一致）
const Role = {
  FARM: 1, PROCESSOR: 2, INSPECTOR: 3, TRANSPORTER: 4, WAREHOUSE: 5, RETAILER: 6,
} as const;
// 环节编号（与合约Stage枚举一致）
const Stage = {
  GROWING: 0, PROCESSING: 1, INSPECTED: 2, IN_TRANSIT: 3, IN_WAREHOUSE: 4, ON_SALE: 5, RECALLED: 6,
} as const;

/* 辅助函数 */
// 部署全新合约
async function deploy() {
  const [regulator, farm, processor, inspector, transporter, warehouse, retailer, outsider] =
    await ethers.getSigners();
  const foodtrace = await ethers.deployContract("Foodtrace", []) as unknown as Foodtrace;
  return { foodtrace, regulator, farm, processor, inspector, transporter, warehouse, retailer, outsider };
}
type Ctx = Awaited<ReturnType<typeof deploy>>;

// 授予全部业务角色
async function setupRoles(ctx: Ctx) {
  const { foodtrace, farm, processor, inspector, transporter, warehouse, retailer } = ctx;
  await foodtrace.setRole(farm.address, Role.FARM);
  await foodtrace.setRole(processor.address, Role.PROCESSOR);
  await foodtrace.setRole(inspector.address, Role.INSPECTOR);
  await foodtrace.setRole(transporter.address, Role.TRANSPORTER);
  await foodtrace.setRole(warehouse.address, Role.WAREHOUSE);
  await foodtrace.setRole(retailer.address, Role.RETAILER);
}

// 部署+角色+基地注册产品1号
async function setupProduct(ctx: Ctx) {
  await setupRoles(ctx);
  const c = ctx.foodtrace.connect(ctx.farm);
  await c.registerProduct("Organic Apple", "B-2026-0901", "planted in spring", "Yantai Farm", "0xhash-001");
}

// 完整流水线：基地->加工->质检->运输->仓储->零售（产品1号）
async function runFullChain(ctx: Ctx) {
  const { foodtrace, farm, processor, inspector, transporter, warehouse, retailer } = ctx;
  await foodtrace.connect(farm).handOver(1n, processor.address);
  await foodtrace.connect(processor).addRecord(1n, Stage.PROCESSING, "pasteurized at 72C", "factory A", "0xhash-002");
  await foodtrace.connect(processor).handOver(1n, inspector.address);
  await foodtrace.connect(inspector).inspectProduct(1n, "0xreport-001", true);
  await foodtrace.connect(inspector).handOver(1n, transporter.address);
  await foodtrace.connect(transporter).addRecord(1n, Stage.IN_TRANSIT, "cold chain 4C", "truck T1", "0xhash-003");
  await foodtrace.connect(transporter).handOver(1n, warehouse.address);
  await foodtrace.connect(warehouse).addRecord(1n, Stage.IN_WAREHOUSE, "stored at 2C", "warehouse W1", "0xhash-004");
  await foodtrace.connect(warehouse).handOver(1n, retailer.address);
  await foodtrace.connect(retailer).addRecord(1n, Stage.ON_SALE, "on shelf", "store S1", "0xhash-005");
}

// 断言交易被回滚且错误消息包含指定文本
async function expectRevert(promise: Promise<unknown>, message: string) {
  try {
    await promise;
    assert.fail(`should revert with "${message}"`);
  } catch (error) {
    const err = error as Error;
    assert.ok(
      err.message.includes(message),
      `expected revert to contain "${message}", actual: ${err.message}`,
    );
  }
}

// 断言交易恰好触发指定名称的事件并返回解析结果
async function expectEvent(ctx: Ctx, tx: Promise<ContractTransactionResponse>, eventName: string) {
  const receipt = await (await tx).wait();
  assert.equal(receipt!.logs.length, 1, `expected exactly 1 event log`);
  const parsed = ctx.foodtrace.interface.parseLog({
    topics: receipt!.logs[0].topics,
    data: receipt!.logs[0].data,
  });
  assert.ok(parsed, "log did not match any event");
  assert.equal(parsed.name, eventName);
  return parsed;
}

/* 权限控制 */
describe("Access Control", function () {
  it("should set deployer as the initial regulator", async function () {
    const ctx = await deploy();
    assert.equal(await ctx.foodtrace.regulator(), ctx.regulator.address);
  });

  it("should reject non-regulator calling setRole", async function () {
    const ctx = await deploy();
    await expectRevert(
      ctx.foodtrace.connect(ctx.farm).setRole(ctx.farm.address, Role.FARM),
      "Caller is not the regulator",
    );
  });

  it("should reject non-regulator calling recallProduct", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    await expectRevert(
      ctx.foodtrace.connect(ctx.farm).recallProduct(1n, "0xreason"),
      "Caller is not the regulator",
    );
  });

  it("should emit RegulatorChanged with correct old/new addresses", async function () {
    const ctx = await deploy();
    const event = await expectEvent(
      ctx,
      ctx.foodtrace.transferRegulator(ctx.inspector.address),
      "RegulatorChanged",
    );
    assert.equal(event.args[0], ctx.regulator.address); // 旧监管地址
    assert.equal(event.args[1], ctx.inspector.address); // 新监管地址
  });

  it("should revoke power from the old regulator after transfer", async function () {
    const ctx = await deploy();
    await ctx.foodtrace.transferRegulator(ctx.inspector.address);
    // 旧监管失去权限
    await expectRevert(
      ctx.foodtrace.connect(ctx.regulator).setRole(ctx.farm.address, Role.FARM),
      "Caller is not the regulator",
    );
    // 新监管获得权限
    await ctx.foodtrace.connect(ctx.inspector).setRole(ctx.farm.address, Role.FARM);
    assert.equal(await ctx.foodtrace.roles(ctx.farm.address), 1n);
  });

  it("should reject transfer to zero address or the same regulator", async function () {
    const ctx = await deploy();
    const ZERO = "0x0000000000000000000000000000000000000000";
    await expectRevert(ctx.foodtrace.transferRegulator(ZERO), "Invalid address");
    await expectRevert(
      ctx.foodtrace.transferRegulator(ctx.regulator.address),
      "Same regulator",
    );
  });
});

/* 角色管理 */
describe("Role Management", function () {
  it("should grant a role via setRole", async function () {
    const ctx = await deploy();
    await ctx.foodtrace.setRole(ctx.farm.address, Role.FARM);
    assert.equal(await ctx.foodtrace.roles(ctx.farm.address), 1n);
  });

  it("should reject granting NONE or zero address", async function () {
    const ctx = await deploy();
    const ZERO = "0x0000000000000000000000000000000000000000";
    await expectRevert(ctx.foodtrace.setRole(ctx.farm.address, 0), "Invalid role");
    await expectRevert(ctx.foodtrace.setRole(ZERO, Role.FARM), "Invalid address");
  });

  it("should reject re-granting the same role", async function () {
    const ctx = await deploy();
    await ctx.foodtrace.setRole(ctx.farm.address, Role.FARM);
    await expectRevert(
      ctx.foodtrace.setRole(ctx.farm.address, Role.FARM),
      "Same role",
    );
  });

  it("should remove a role and reject double removal", async function () {
    const ctx = await deploy();
    await ctx.foodtrace.setRole(ctx.farm.address, Role.FARM);
    await ctx.foodtrace.removeRole(ctx.farm.address);
    assert.equal(await ctx.foodtrace.roles(ctx.farm.address), 0n);
    await expectRevert(ctx.foodtrace.removeRole(ctx.farm.address), "No role to remove");
  });
});

/* 产品注册 */
describe("Product Registration", function () {
  it("should reject registration from non-farm caller", async function () {
    const ctx = await deploy();
    await ctx.foodtrace.setRole(ctx.farm.address, Role.FARM);
    await expectRevert(
      ctx.foodtrace.connect(ctx.outsider).registerProduct("Apple", "B1", "d", "loc", "0xh"),
      "Caller does not have the required role",
    );
  });

  it("should register a product with correct initial state", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    const product = await ctx.foodtrace.getProduct(1n);
    assert.equal(product.name, "Organic Apple");
    assert.equal(product.batch_no, "B-2026-0901");
    assert.equal(product.origin_farm.toLowerCase(), ctx.farm.address.toLowerCase());
    assert.equal(product.current_holder.toLowerCase(), ctx.farm.address.toLowerCase());
    assert.equal(Number(product.stage), Stage.GROWING);
    assert.equal(product.recalled, false);
    assert.equal(product.records.length, 1);
    assert.equal(product.records[0].description, "planted in spring");
    assert.equal(product.records[0].data_hash, "0xhash-001");
    assert.ok(product.records[0].timestamp > 0n);
  });

  it("should increment product id and count", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    const c = ctx.foodtrace.connect(ctx.farm);
    await c.registerProduct("Apple 2", "B-2026-0902", "d", "loc", "0xh2");
    assert.equal(await ctx.foodtrace.getProductCount(), 2n);
    assert.equal(await ctx.foodtrace.next_product_id(), 3n);
    assert.equal((await ctx.foodtrace.getProduct(2n)).name, "Apple 2");
  });

  it("should reject duplicate batch number", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    await expectRevert(
      ctx.foodtrace.connect(ctx.farm).registerProduct("Another", "B-2026-0901", "d", "loc", "0xh"),
      "Batch number already exists",
    );
  });

  it("should allow same product name with different batch", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    await ctx.foodtrace.connect(ctx.farm).registerProduct("Organic Apple", "B-2026-0902", "d", "loc", "0xh2");
    assert.equal(await ctx.foodtrace.getProductCount(), 2n);
  });

  it("should reject empty name / batch / data hash", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    const c = ctx.foodtrace.connect(ctx.farm);
    await expectRevert(c.registerProduct("", "B2", "d", "loc", "0xh"), "Product name cannot be empty");
    await expectRevert(c.registerProduct("Apple", "", "d", "loc", "0xh"), "Batch number cannot be empty");
    await expectRevert(c.registerProduct("Apple", "B2", "d", "loc", ""), "Data hash cannot be empty");
  });
});

/* 环节记录 */
describe("Trace Records", function () {
  it("should reject addRecord from non-holder", async function () {
    const ctx = await deploy();
    await setupProduct(ctx); // 角色已全部授予，但持有者是基地
    await expectRevert(
      ctx.foodtrace.connect(ctx.processor).addRecord(1n, Stage.GROWING, "d", "loc", "0xh"),
      "Caller is not the current holder",
    );
  });

  it("should reject addRecord on non-existent product", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    await expectRevert(
      ctx.foodtrace.connect(ctx.farm).addRecord(999n, Stage.GROWING, "d", "loc", "0xh"),
      "Product does not exist",
    );
  });

  it("should allow multiple records at the same stage", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    const c = ctx.foodtrace.connect(ctx.farm);
    await c.addRecord(1n, Stage.GROWING, "second fertilizing", "field 2", "0xhash-002");
    await c.addRecord(1n, Stage.GROWING, "third irrigation", "field 2", "0xhash-003");
    const product = await ctx.foodtrace.getProduct(1n);
    assert.equal(product.records.length, 3);
  });

  it("should reject addRecord with empty data hash", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    await expectRevert(
      ctx.foodtrace.connect(ctx.farm).addRecord(1n, Stage.GROWING, "d", "loc", ""),
      "Data hash cannot be empty",
    );
  });

  it("should reject addRecord when stage does not match caller role", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    // 货已交给加工厂，加工厂想直接写运输记录
    await ctx.foodtrace.connect(ctx.farm).handOver(1n, ctx.processor.address);
    await expectRevert(
      ctx.foodtrace.connect(ctx.processor).addRecord(1n, Stage.IN_TRANSIT, "d", "loc", "0xh"),
      "Stage does not match role",
    );
  });

  it("should reject inspector bypassing inspectProduct via addRecord", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    // 基地直接把货交给质检（原料检验链路），质检想用环节记录替代裁决
    await ctx.foodtrace.connect(ctx.farm).handOver(1n, ctx.inspector.address);
    await expectRevert(
      ctx.foodtrace.connect(ctx.inspector).addRecord(1n, Stage.INSPECTED, "self pass", "", "0xh"),
      "Inspector must use inspectProduct",
    );
  });

  it("should reject regressing stage after role change", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    // 加工厂已写加工记录（环节为加工），监管将其改为基地角色后再写种植记录
    await ctx.foodtrace.connect(ctx.farm).handOver(1n, ctx.processor.address);
    await ctx.foodtrace.connect(ctx.processor).addRecord(1n, Stage.PROCESSING, "processed", "loc", "0xh");
    await ctx.foodtrace.setRole(ctx.processor.address, Role.FARM);
    await expectRevert(
      ctx.foodtrace.connect(ctx.processor).addRecord(1n, Stage.GROWING, "regress", "loc", "0xh"),
      "Cannot regress stage",
    );
  });

  it("should reject jumping to warehouse stage after role change", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    // 监管把基地角色改为仓储，持有者借新角色直接写仓储记录跳过质检
    await ctx.foodtrace.setRole(ctx.farm.address, Role.WAREHOUSE);
    await expectRevert(
      ctx.foodtrace.connect(ctx.farm).addRecord(1n, Stage.IN_WAREHOUSE, "fake stored", "loc", "0xh"),
      "Must pass inspection before transit",
    );
  });

  it("should reject jumping to transit stage after role change", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    // 监管把基地角色改为物流，持有者借新角色直接写运输记录跳过质检
    await ctx.foodtrace.setRole(ctx.farm.address, Role.TRANSPORTER);
    await expectRevert(
      ctx.foodtrace.connect(ctx.farm).addRecord(1n, Stage.IN_TRANSIT, "fake transit", "loc", "0xh"),
      "Must pass inspection before transit",
    );
  });

  it("should reject skipping transit via role change after inspection", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    // 质检通过后监管把质检员改为仓储角色，试图直接写仓储记录跳过运输
    await ctx.foodtrace.connect(ctx.farm).handOver(1n, ctx.inspector.address);
    await ctx.foodtrace.connect(ctx.inspector).inspectProduct(1n, "0xreport", true);
    await ctx.foodtrace.setRole(ctx.inspector.address, Role.WAREHOUSE);
    await expectRevert(
      ctx.foodtrace.connect(ctx.inspector).addRecord(1n, Stage.IN_WAREHOUSE, "skip transit", "loc", "0xh"),
      "Invalid stage transition",
    );
  });
});

/* 责任交接 */
describe("Handover State Machine", function () {
  it("should reject handover to no-role / self / zero address", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    const ZERO = "0x0000000000000000000000000000000000000000";
    await expectRevert(
      ctx.foodtrace.connect(ctx.farm).handOver(1n, ctx.outsider.address),
      "Next holder has no role",
    );
    await expectRevert(
      ctx.foodtrace.connect(ctx.farm).handOver(1n, ctx.farm.address),
      "Cannot hand over to self",
    );
    await expectRevert(
      ctx.foodtrace.connect(ctx.farm).handOver(1n, ZERO),
      "Invalid next holder",
    );
  });

  it("should reject handover from non-holder or non-existent product", async function () {
    const ctx = await deploy();
    await setupProduct(ctx); // 角色已全部授予，但持有者是基地
    await expectRevert(
      ctx.foodtrace.connect(ctx.processor).handOver(1n, ctx.inspector.address),
      "Caller is not the current holder",
    );
    await expectRevert(
      ctx.foodtrace.connect(ctx.farm).handOver(999n, ctx.processor.address),
      "Product does not exist",
    );
  });

  it("should reject handover from holder whose role was removed", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    // 监管移除基地角色后，被除名机构不得再处置名下在途产品
    await ctx.foodtrace.removeRole(ctx.farm.address);
    await expectRevert(
      ctx.foodtrace.connect(ctx.farm).handOver(1n, ctx.processor.address),
      "Caller has no role",
    );
  });

  it("should reject skipping inspection", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    // 基地想把货直接交给物流或零售
    await expectRevert(
      ctx.foodtrace.connect(ctx.farm).handOver(1n, ctx.transporter.address),
      "Must pass inspection first",
    );
    await expectRevert(
      ctx.foodtrace.connect(ctx.farm).handOver(1n, ctx.retailer.address),
      "Must pass inspection first",
    );
  });

  it("should reject transporting after processing without inspection", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    await ctx.foodtrace.connect(ctx.farm).handOver(1n, ctx.processor.address);
    await ctx.foodtrace.connect(ctx.processor).addRecord(1n, Stage.PROCESSING, "processed", "loc", "0xh");
    await expectRevert(
      ctx.foodtrace.connect(ctx.processor).handOver(1n, ctx.transporter.address),
      "Must pass inspection first",
    );
  });

  it("should reject skipping transport after inspection", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    // 原料检验链路：基地->质检->合格，质检想直接把货交给仓储或零售
    await ctx.foodtrace.connect(ctx.farm).handOver(1n, ctx.inspector.address);
    await ctx.foodtrace.connect(ctx.inspector).inspectProduct(1n, "0xreport", true);
    await expectRevert(
      ctx.foodtrace.connect(ctx.inspector).handOver(1n, ctx.warehouse.address),
      "Invalid stage transition",
    );
    await expectRevert(
      ctx.foodtrace.connect(ctx.inspector).handOver(1n, ctx.retailer.address),
      "Invalid stage transition",
    );
  });

  it("should allow direct delivery skipping warehouse", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    // 产地直供：质检合格后经运输直达零售，跳过仓储
    await ctx.foodtrace.connect(ctx.farm).handOver(1n, ctx.inspector.address);
    await ctx.foodtrace.connect(ctx.inspector).inspectProduct(1n, "0xreport", true);
    await ctx.foodtrace.connect(ctx.inspector).handOver(1n, ctx.transporter.address);
    await ctx.foodtrace.connect(ctx.transporter).addRecord(1n, Stage.IN_TRANSIT, "cold chain", "truck", "0xh");
    await ctx.foodtrace.connect(ctx.transporter).handOver(1n, ctx.retailer.address);
    const product = await ctx.foodtrace.getProduct(1n);
    // 零售未写记录前，环节仍停在运输
    assert.equal(Number(product.stage), Stage.IN_TRANSIT);
  });

  it("should reject backward handover", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    // 货已到运输环节，物流想把货退回加工厂
    await ctx.foodtrace.connect(ctx.farm).handOver(1n, ctx.processor.address);
    await ctx.foodtrace.connect(ctx.processor).addRecord(1n, Stage.PROCESSING, "processed", "loc", "0xh");
    await ctx.foodtrace.connect(ctx.processor).handOver(1n, ctx.inspector.address);
    await ctx.foodtrace.connect(ctx.inspector).inspectProduct(1n, "0xreport", true);
    await ctx.foodtrace.connect(ctx.inspector).handOver(1n, ctx.transporter.address);
    await ctx.foodtrace.connect(ctx.transporter).addRecord(1n, Stage.IN_TRANSIT, "cold chain", "truck", "0xh");
    await expectRevert(
      ctx.foodtrace.connect(ctx.transporter).handOver(1n, ctx.processor.address),
      "Invalid stage transition",
    );
  });

  it("should reject handover at terminal ON_SALE stage", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    await runFullChain(ctx);
    await expectRevert(
      ctx.foodtrace.connect(ctx.retailer).handOver(1n, ctx.warehouse.address),
      "Stage terminated, cannot hand over",
    );
  });
});

/* 质检裁决 */
describe("Inspection", function () {
  it("should reject inspection from non-inspector or non-holder", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    await expectRevert(
      ctx.foodtrace.connect(ctx.outsider).inspectProduct(1n, "0xreport", true),
      "Caller does not have the required role",
    );
    await expectRevert(
      ctx.foodtrace.connect(ctx.inspector).inspectProduct(1n, "0xreport", true),
      "Caller is not the current holder",
    );
  });

  it("should reject empty report hash", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    await ctx.foodtrace.connect(ctx.farm).handOver(1n, ctx.inspector.address);
    await expectRevert(
      ctx.foodtrace.connect(ctx.inspector).inspectProduct(1n, "", true),
      "Report hash cannot be empty",
    );
  });

  it("should allow raw-material inspection before processing", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    // 原料检验：基地未加工直接把货交给质检
    await ctx.foodtrace.connect(ctx.farm).handOver(1n, ctx.inspector.address);
    const event = await expectEvent(
      ctx,
      ctx.foodtrace.connect(ctx.inspector).inspectProduct(1n, "0xreport", true),
      "ProductInspected",
    );
    assert.equal(event.args[1], true);
    const product = await ctx.foodtrace.getProduct(1n);
    assert.equal(Number(product.stage), Stage.INSPECTED);
    assert.equal(product.recalled, false);
  });

  it("should allow finished-product inspection after processing", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    // 成品检验：加工完再质检
    await ctx.foodtrace.connect(ctx.farm).handOver(1n, ctx.processor.address);
    await ctx.foodtrace.connect(ctx.processor).addRecord(1n, Stage.PROCESSING, "processed", "loc", "0xh");
    await ctx.foodtrace.connect(ctx.processor).handOver(1n, ctx.inspector.address);
    await ctx.foodtrace.connect(ctx.inspector).inspectProduct(1n, "0xreport", true);
    assert.equal(Number((await ctx.foodtrace.getProduct(1n)).stage), Stage.INSPECTED);
  });

  it("should recall product when inspection fails and block all writes", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    await ctx.foodtrace.connect(ctx.farm).handOver(1n, ctx.inspector.address);
    const event = await expectEvent(
      ctx,
      ctx.foodtrace.connect(ctx.inspector).inspectProduct(1n, "0xreport-fail", false),
      "ProductInspected",
    );
    assert.equal(event.args[1], false);
    const product = await ctx.foodtrace.getProduct(1n);
    assert.equal(product.recalled, true);
    assert.equal(Number(product.stage), Stage.RECALLED);
    // 质检不合格即终态，后续交接与复检都被拦截
    await expectRevert(
      ctx.foodtrace.connect(ctx.inspector).handOver(1n, ctx.transporter.address),
      "Product has been recalled",
    );
    await expectRevert(
      ctx.foodtrace.connect(ctx.inspector).inspectProduct(1n, "0xreport-2", true),
      "Product has been recalled",
    );
  });
});

/* 产品召回 */
describe("Recall", function () {
  it("should recall a product at any stage and block subsequent writes", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    await runFullChain(ctx); // 产品已走到销售环节
    const event = await expectEvent(
      ctx,
      ctx.foodtrace.recallProduct(1n, "0xreason-001"),
      "ProductRecalled",
    );
    assert.equal(event.args[1].toLowerCase(), ctx.regulator.address.toLowerCase());
    const product = await ctx.foodtrace.getProduct(1n);
    assert.equal(product.recalled, true);
    assert.equal(Number(product.stage), Stage.RECALLED);
    // 查询不受影响，写操作全部被拦截
    await expectRevert(
      ctx.foodtrace.connect(ctx.retailer).addRecord(1n, Stage.ON_SALE, "again", "loc", "0xh"),
      "Product has been recalled",
    );
    await expectRevert(
      ctx.foodtrace.connect(ctx.retailer).handOver(1n, ctx.warehouse.address),
      "Product has been recalled",
    );
  });

  it("should reject double recall and empty reason", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    await ctx.foodtrace.recallProduct(1n, "0xreason");
    await expectRevert(ctx.foodtrace.recallProduct(1n, "0xreason2"), "Product already recalled");
    // 新注册产品验证空召回原因被拒
    await ctx.foodtrace.connect(ctx.farm).registerProduct("P2", "B-2026-0902", "d", "loc", "0xh2");
    await expectRevert(ctx.foodtrace.recallProduct(2n, ""), "Reason hash cannot be empty");
  });

  it("should reject recall on non-existent product", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    await expectRevert(ctx.foodtrace.recallProduct(999n, "0xr"), "Product does not exist");
  });
});

/* 查询接口 */
describe("Query Functions", function () {
  it("should return full product info with complete record chain", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    await runFullChain(ctx);
    const product = await ctx.foodtrace.getProduct(1n);
    assert.equal(product.name, "Organic Apple");
    assert.equal(product.batch_no, "B-2026-0901");
    assert.equal(product.current_holder.toLowerCase(), ctx.retailer.address.toLowerCase());
    assert.equal(Number(product.stage), Stage.ON_SALE);
    // 1条注册记录+4条环节记录+1条质检记录+5条交接留痕
    assert.equal(product.records.length, 11);
  });

  it("should filter records by stage", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    await runFullChain(ctx);
    const inspected = await ctx.foodtrace.getStageRecords(1n, Stage.INSPECTED);
    const inTransit = await ctx.foodtrace.getStageRecords(1n, Stage.IN_TRANSIT);
    const growing = await ctx.foodtrace.getStageRecords(1n, Stage.GROWING);
    assert.equal(inspected.length, 2); // 质检记录+质检后交接留痕
    assert.equal(inTransit.length, 2); // 运输记录+交接留痕
    assert.equal(growing.length, 2);   // 注册记录+基地交接留痕
  });

  it("should look up product by batch number", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    const product = await ctx.foodtrace.getProductByBatch("B-2026-0901");
    assert.equal(Number(product.id), 1);
    assert.equal(product.name, "Organic Apple");
    await expectRevert(
      ctx.foodtrace.getProductByBatch("B-UNKNOWN"),
      "Product does not exist",
    );
  });

  it("should return zero records filter and correct count", async function () {
    const ctx = await deploy();
    await setupProduct(ctx);
    const onSale = await ctx.foodtrace.getStageRecords(1n, Stage.ON_SALE);
    assert.equal(onSale.length, 0);
    assert.equal(await ctx.foodtrace.getProductCount(), 1n);
  });
});
