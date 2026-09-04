// Hardhat 配置文件：Solidity 编译器 + ethers.js / test-runner 插件
import hardhatEthers from "@nomicfoundation/hardhat-ethers";
import hardhatTestRunner from "@nomicfoundation/hardhat-node-test-runner";
import { defineConfig } from "hardhat/config";

export default defineConfig({
  plugins: [hardhatEthers, hardhatTestRunner],
  solidity: {
    version: "0.8.11",
  },
});
