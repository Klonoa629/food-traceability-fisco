import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { network } from "hardhat";
import type { AuditAnchor } from "../types/AuditAnchor.js";

const { ethers } = await network.create();

async function deploy() {
  const [regulator, outsider] = await ethers.getSigners();
  const anchor = await ethers.deployContract("AuditAnchor", []) as unknown as AuditAnchor;
  return { anchor, regulator, outsider };
}

describe("Audit Anchor", () => {
  it("部署者为监管，初始没有锚", async () => {
    const { anchor, regulator } = await deploy();
    assert.equal(await anchor.regulator(), regulator.address);
    assert.equal(await anchor.anchorCount(), 0n);
  });

  it("非监管不能写锚，也不能改变锚计数", async () => {
    const { anchor, outsider } = await deploy();
    await assert.rejects(anchor.connect(outsider).anchor("fake", 1n), /Caller is not the regulator/);
    assert.equal(await anchor.anchorCount(), 0n);
  });

  it("追加锚保留历史快照", async () => {
    const { anchor } = await deploy();
    await (await anchor.anchor("a".repeat(64), 2n)).wait();
    const first = await anchor.getAnchor(1n);
    await (await anchor.anchor("b".repeat(64), 4n)).wait();
    assert.equal(await anchor.anchorCount(), 2n);
    assert.deepEqual([...(await anchor.getAnchor(1n))], [...first]);
    const second = await anchor.getAnchor(2n);
    assert.equal(second[0], "b".repeat(64));
    assert.equal(second[1], 4n);
    assert.ok(second[2] >= first[2]);
  });

  it("事件包含锚编号、链头与行数", async () => {
    const { anchor } = await deploy();
    const receipt = await (await anchor.anchor("c".repeat(64), 7n)).wait();
    assert.equal(receipt!.logs.length, 1);
    const event = anchor.interface.parseLog(receipt!.logs[0]);
    assert.equal(event!.name, "AuditAnchored");
    assert.deepEqual([...event!.args], [1n, "c".repeat(64), 7n]);
  });

  it("拒绝零编号和越界编号查询", async () => {
    const { anchor } = await deploy();
    await assert.rejects(anchor.getAnchor(0n), /Anchor does not exist/);
    await assert.rejects(anchor.getAnchor(1n), /Anchor does not exist/);
    await (await anchor.anchor("d".repeat(64), 1n)).wait();
    await assert.rejects(anchor.getAnchor(2n), /Anchor does not exist/);
  });
});
