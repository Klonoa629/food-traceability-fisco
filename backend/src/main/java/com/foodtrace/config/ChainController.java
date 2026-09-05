package com.foodtrace.config;

import org.fisco.bcos.sdk.v3.client.Client;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/chain")
public class ChainController {
    private final Client client;
    public ChainController(Client client) {
        this.client = client;
    }

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("blockNumber", client.getBlockNumber().getBlockNumber());
    }
}
