package com.hmdp;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@Slf4j
@SpringBootTest
class HmDianPingApplicationTests {

@Test
    public void test(){
    log.info(UUID.randomUUID().toString());
    System.out.println(UUID.randomUUID());
}
}
