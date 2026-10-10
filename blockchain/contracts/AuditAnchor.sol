// SPDX-License-Identifier: MIT
pragma solidity ^0.8.11;

// 审计链头锚定合约：定期将审计哈希链的链头写入链上，
// 即使整库重写也无法伪造与锚一致的哈希链。
contract AuditAnchor {
    // 链上监管（部署者）
    address public regulator;
    // 锚定记录总数
    uint256 public anchorCount;
    // 锚定记录
    mapping(uint256 => Anchor) public anchors;

    struct Anchor {
        string headHash;   // 审计链头 SHA-256
        uint256 rowCount;  // 锚定时审计行数
        uint256 timestamp; // 上链时间戳
    }

    event AuditAnchored(uint256 indexed id, string headHash, uint256 rowCount);

    modifier onlyRegulator() {
        require(msg.sender == regulator, "Caller is not the regulator");
        _;
    }

    constructor() {
        regulator = msg.sender;
    }

    // 写入一次锚定
    function anchor(string calldata headHash, uint256 rowCount) external onlyRegulator returns (uint256) {
        anchorCount++;
        anchors[anchorCount] = Anchor(headHash, rowCount, block.timestamp);
        emit AuditAnchored(anchorCount, headHash, rowCount);
        return anchorCount;
    }

    // 查询指定锚定
    function getAnchor(uint256 id) external view returns (string memory, uint256, uint256) {
        require(id > 0 && id <= anchorCount, "Anchor does not exist");
        Anchor storage a = anchors[id];
        return (a.headHash, a.rowCount, a.timestamp);
    }
}
