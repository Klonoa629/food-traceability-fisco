// SPDX-License-Identifier: MIT
pragma solidity ^0.8.11;

contract Foodtrace {
    /* 类型定义 */
    // 角色（参与机构）
    enum Role {
        NONE,        // 无角色
        FARM,        // 基地（种植/养殖）
        PROCESSOR,   // 加工厂
        INSPECTOR,   // 质检机构
        TRANSPORTER, // 物流
        WAREHOUSE,   // 仓库
        RETAILER     // 零售商
    }
    // 环节（产品生命周期）
    enum Stage {
        GROWING,      // 种植
        PROCESSING,   // 加工
        INSPECTED,    // 质检
        IN_TRANSIT,   // 运输
        IN_WAREHOUSE, // 仓储
        ON_SALE,      // 销售
        RECALLED      // 召回
    }
    // 溯源记录
    struct TraceRecord {
        Stage stage;        // 环节
        string description; // 描述
        address operator;   // 操作机构
        string location;    // 产地/位置
        string data_hash;  // 数据哈希
        uint256 timestamp;  // 上链时间戳
    }
    // 产品属性
    struct Product {
        uint256 id;             // 产品 ID（链上自增）
        string name;            // 产品名称（可重名）
        string batch_no;        // 批次号（业务唯一标识）
        address origin_farm;    // 初始基地（注册者，产品源头）
        address current_holder; // 当前责任方
        Stage stage;            // 当前环节
        bool recalled;          // 召回标记
        TraceRecord[] records;  // 溯源记录数组
    }

    /* 状态变量 */
    address public regulator;                            // 监管机构（部署者，可移交）
    mapping(address => Role) public roles;               // 机构角色
    mapping(bytes32 => uint256) public batch_to_product; // 批次号 -> 产品 ID（未占用为 0）
    uint256 public next_product_id = 1;                  // 产品 ID 自增（起始为 1）
    mapping(uint256 => Product) public products;         // 产品映射

    /* 事件 */
    // 监管机构变更
    event RegulatorChanged(address indexed old_regulator, address indexed new_regulator);
    // 参与机构角色变更
    event RoleUpdated(address indexed account, Role role);
    // 产品注册
    event ProductRegistered(uint256 indexed product_id, string name, address indexed origin_farm);
    // 溯源记录添加
    event RecordAdded(uint256 indexed product_id, Stage stage, address indexed operator);
    // 产品流转（交接）
    event ProductHandedOver(uint256 indexed product_id, address indexed from, address indexed to);
    // 产品质检裁决
    event ProductInspected(uint256 indexed product_id, bool qualified);
    // 产品召回
    event ProductRecalled(uint256 indexed product_id, address indexed operator);

    /* 修饰符 */
    // 仅监管机构可调用
    modifier onlyRegulator() {
        require(msg.sender == regulator, "Caller is not the regulator");
        _;
    }
    // 仅特定角色的机构可调用
    modifier onlyRole(Role r) {
        require(roles[msg.sender] == r, "Caller does not have the required role");
        _;
    }
    // 仅当前责任方可调用
    modifier onlyCurrentHolder(uint256 id) {
        require(products[id].current_holder == msg.sender, "Caller is not the current holder");
        _;
    }
    // 产品必须存在
    modifier productExists(uint256 id) {
        require(id > 0 && id < next_product_id, "Product does not exist");
        _;
    }
    // 产品必须未被召回
    modifier notRecalled(uint256 id) {
        require(!products[id].recalled, "Product has been recalled");
        _;
    }

    /* 构造函数 */
    constructor() {
        regulator = msg.sender; // 部署者为初始监管机构
    }

    /* 内部函数 */
    // 角色 -> 环节 
    function roleToStage(Role r) internal pure returns (Stage) {
        if (r == Role.FARM) return Stage.GROWING;           // 基地 -> 种植
        if (r == Role.PROCESSOR) return Stage.PROCESSING;   // 加工厂 -> 加工
        if (r == Role.INSPECTOR) return Stage.INSPECTED;    // 质检机构 -> 质检
        if (r == Role.TRANSPORTER) return Stage.IN_TRANSIT; // 物流 -> 运输
        if (r == Role.WAREHOUSE) return Stage.IN_WAREHOUSE; // 仓库 -> 仓储
        if (r == Role.RETAILER) return Stage.ON_SALE;       // 零售商 -> 销售
        revert("Invalid role");  
    }

    /* 监管机构权限管理 */
    // 监管权移交
    function transferRegulator(address new_regulator) external onlyRegulator {
        require(new_regulator != address(0), "Invalid address");
        require(new_regulator != regulator, "Same regulator");
        address previous_regulator = regulator;
        regulator = new_regulator;        
        emit RegulatorChanged(previous_regulator, new_regulator);
    }
    // 机构角色设置
    function setRole(address account, Role role) external onlyRegulator {
        require(account != address(0), "Invalid address");
        require(role != Role.NONE, "Invalid role");
        require(roles[account] != role, "Same role");
        roles[account] = role;
        emit RoleUpdated(account, role);
    }
    // 机构角色移除
    function removeRole(address account) external onlyRegulator {
        require(account != address(0), "Invalid address");
        require(roles[account] != Role.NONE, "No role to remove");
        roles[account] = Role.NONE;
        emit RoleUpdated(account, Role.NONE);
    }

    /* 业务逻辑 */
    // 产品注册（仅基地可调用）
    function registerProduct(
        string calldata name,
        string calldata batch_no,
        string calldata description,
        string calldata location,
        string calldata data_hash
    ) external onlyRole(Role.FARM) returns (uint256) {
        require(bytes(name).length > 0, "Product name cannot be empty");
        require(bytes(batch_no).length > 0, "Batch number cannot be empty");
        require(bytes(data_hash).length > 0, "Data hash cannot be empty");
        uint256 pid = next_product_id;
        next_product_id++;
        // 批次号查重并占用
        {
            bytes32 key = keccak256(abi.encodePacked(batch_no));
            require(batch_to_product[key] == 0, "Batch number already exists");
            batch_to_product[key] = pid;
        }
        // 创建新产品并写入首条种植记录
        {
            Product storage p = products[pid];
            p.id = pid;
            p.name = name;
            p.batch_no = batch_no;
            p.origin_farm = msg.sender;
            p.current_holder = msg.sender;
            p.stage = Stage.GROWING;
            p.recalled = false;
            p.records.push(TraceRecord({
                stage: Stage.GROWING,
                description: description,
                operator: msg.sender,
                location: location,
                data_hash: data_hash,
                timestamp: block.timestamp
            }));
        }
        emit ProductRegistered(pid, name, msg.sender);
        return pid;
    }
    // 环节记录
    function addRecord(
        uint256 product_id,
        Stage stage,
        string calldata description,
        string calldata location,
        string calldata data_hash
    ) external productExists(product_id) onlyCurrentHolder(product_id) notRecalled(product_id) {
        Product storage p = products[product_id];
        require(roleToStage(roles[msg.sender]) == stage, "Stage does not match role");
        require(roles[msg.sender] != Role.INSPECTOR, "Inspector must use inspectProduct");
        require(stage >= p.stage, "Cannot regress stage");
        // 未过质检前不得写运输及之后的环节记录（防中途改角色跳过质检）
        require(p.stage >= Stage.INSPECTED || stage < Stage.IN_TRANSIT, "Must pass inspection before transit");
        // 环节只能原地补记或逐级推进，产地直供允许运输环节直接写销售记录
        require(uint256(stage) <= uint256(p.stage) + 1 || (p.stage == Stage.IN_TRANSIT && stage == Stage.ON_SALE), "Invalid stage transition");
        require(bytes(data_hash).length > 0, "Data hash cannot be empty");
        // 更新产品状态
        p.stage = stage;
        p.records.push(TraceRecord({
            stage: stage,
            description: description,
            operator: msg.sender,
            location: location,
            data_hash: data_hash,
            timestamp: block.timestamp
        }));
        emit RecordAdded(product_id, stage, msg.sender);
    }
    // 产品流转（交接）
    function handOver(
        uint256 product_id, 
        address next_holder
    ) external productExists(product_id) onlyCurrentHolder(product_id) notRecalled(product_id) {
        Product storage p = products[product_id];        
        // 被移除角色的机构不得再处置名下在途产品
        require(roles[msg.sender] != Role.NONE, "Caller has no role");
        require(next_holder != address(0), "Invalid next holder");
        require(roles[next_holder] != Role.NONE, "Next holder has no role");
        require(next_holder != msg.sender, "Cannot hand over to self");
        Role next_role = roles[next_holder];
        Stage next_stage = roleToStage(next_role);
        // 约束规则（质检必需，加工和仓储非必需）
        if (p.stage < Stage.INSPECTED) {
            require(next_stage > p.stage && next_stage <= Stage.INSPECTED, "Must pass inspection first");
        }else if (p.stage == Stage.INSPECTED) {
            require(next_stage == Stage.IN_TRANSIT, "Invalid stage transition");
        }else if (p.stage == Stage.IN_TRANSIT) {
            require(next_stage == Stage.IN_WAREHOUSE || next_stage == Stage.ON_SALE, "Invalid stage transition");
        }else if (p.stage == Stage.IN_WAREHOUSE) {
            require(next_stage == Stage.ON_SALE, "Invalid stage transition");
        }else {
            revert("Stage terminated, cannot hand over");
        }
        // 更新产品状态
        address previous_holder = p.current_holder;
        p.current_holder = next_holder;
        p.records.push(TraceRecord({
            stage: p.stage,
            description: "Product handed over",
            operator: msg.sender,
            location: "",
            data_hash: "",
            timestamp: block.timestamp
        }));
        emit ProductHandedOver(product_id, previous_holder, next_holder);
    }
    // 质检裁决（仅质检机构可调用）
    function inspectProduct(
        uint256 product_id, 
        string calldata report_hash, 
        bool qualified
    ) external productExists(product_id) onlyRole(Role.INSPECTOR) onlyCurrentHolder(product_id) notRecalled(product_id) {
        require(bytes(report_hash).length > 0, "Report hash cannot be empty");
        Product storage p = products[product_id];
        require(p.stage < Stage.INSPECTED, "Product already inspected");
        // 更新产品状态
        if (qualified) {
            p.stage = Stage.INSPECTED;
            p.records.push(TraceRecord({
                stage: Stage.INSPECTED,
                description: "Inspection passed",
                operator: msg.sender,
                location: "",
                data_hash: report_hash,
                timestamp: block.timestamp
            }));
        } else {
            p.recalled = true;
            p.stage = Stage.RECALLED;
            p.records.push(TraceRecord({
                stage: Stage.RECALLED,
                description: "Inspection failed",
                operator: msg.sender,
                location: "",
                data_hash: report_hash,
                timestamp: block.timestamp
            }));
        }
        emit ProductInspected(product_id, qualified);
    }
    // 产品召回（仅监管机构可调用）
    function recallProduct(
        uint256 product_id,
        string calldata reason_hash
    ) external productExists(product_id) onlyRegulator {
        require(bytes(reason_hash).length > 0, "Reason hash cannot be empty");
        Product storage p = products[product_id];
        require(!p.recalled, "Product already recalled");
        // 更新产品状态
        p.recalled = true;
        p.stage = Stage.RECALLED;
        p.records.push(TraceRecord({
            stage: Stage.RECALLED,
            description: "Product recalled",
            operator: msg.sender,
            location: "",
            data_hash: reason_hash,
            timestamp: block.timestamp
        }));
        emit ProductRecalled(product_id, msg.sender);
    }

    /* 查询接口 */
    // 产品完整信息
    function getProduct(uint256 product_id) external view productExists(product_id) returns (Product memory) {
        return products[product_id];
    }
    // 已注册产品总数
    function getProductCount() external view returns (uint256) {
        return next_product_id - 1;
    }
    // 环节溯源记录
    function getStageRecords(
        uint256 product_id, 
        Stage stage
    ) external view productExists(product_id) returns (TraceRecord[] memory) {
        Product storage p = products[product_id];
        uint256 count = 0;
        for (uint256 i = 0; i < p.records.length; i++) {
            if (p.records[i].stage == stage) {
                count++;
            }
        }
        TraceRecord[] memory records = new TraceRecord[](count);
        uint256 index = 0;
        for (uint256 i = 0; i < p.records.length; i++) {
            if (p.records[i].stage == stage) {
                records[index] = p.records[i];
                index++;
            }
        }
        return records;
    }
    // 批次号查产品
    function getProductByBatch(string calldata batch_no) external view returns (Product memory) {
        bytes32 key = keccak256(abi.encodePacked(batch_no));
        uint256 product_id = batch_to_product[key];
        require(product_id != 0, "Product does not exist");
        return products[product_id];
    }
}
