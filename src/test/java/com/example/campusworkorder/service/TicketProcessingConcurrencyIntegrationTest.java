/**
 * 并发处理同一张工单的集成测试。
 *
 * 测试数据：
 *   - 1 个 REQUESTER、1 个 WORKER（用户名带 nanoTime 保证唯一）
 *   - 1 张 status=ASSIGNED 的工单，creator=REQUESTER，assignee=WORKER
 *
 * 同步起步方式：
 *   - 双 CountDownLatch：ready(2) 等两个线程就位，start(1) 由主线程发令；
 *     主线程 ready.await() 归零后调用 start.countDown()，
 *     两个线程同时进入 Service
 *
 * 预期成功/冲突/最终状态/日志：
 *   - 成功 1 次：返回 PROCESSING
 *   - 冲突 1 次：BusinessException，HTTP 409
 *     （消息可能是 "The status of ticket isn't assigned"，
 *      也可能是 "Ticket status has changed"，取决于谁先读到状态）
 *   - 最终状态：ticket.status = PROCESSING，assignee 不变
 *   - 日志：ticket_log 中 from=ASSIGNED、to=PROCESSING 的恰好 1 条
 *
 * 线程与数据清理顺序：
 *   1. 建用户 → 建工单
 *   2. 提交 2 个任务 → 等两个 Future 返回
 *   3. 断言
 *   4. finally：shutdown 线程池 → 删 ticket_log → 删 ticket → 删 app_user
 */
package com.example.campusworkorder.service;

import com.example.campusworkorder.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class TicketProcessingConcurrencyIntegrationTest {
    @Autowired
    private TicketService ticketService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Test
    void connectedDatabase_shouldBeReproDatabase() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        assertEquals("cworeproday51", database);
    }
    @Test
    void twoThreadsProcessSameTicket_shouldOnlyOneSucceed() throws Exception{
        String suffix = String.valueOf(System.nanoTime());

        Long requesterId = null;
        Long workerId = null;
        Long ticketId = null;
        ExecutorService pool = null;
        try{
            //造数据
            jdbcTemplate.update(
                    "insert into app_user (username, password_hash, role) values (?, ?, ?)",
                    "concur_requester_" + suffix, "test_hash", "REQUESTER"
            );
            requesterId = jdbcTemplate.queryForObject(
                    "select id from app_user where username = ?",
                    Long.class, "concur_requester_" + suffix
            );
            jdbcTemplate.update(
                    "insert into app_user (username, password_hash, role) values (?, ?, ?)",
                    "concur_worker_" + suffix, "test_hash", "WORKER");
            workerId = jdbcTemplate.queryForObject(
                    "select id from app_user where username = ?",
                    Long.class, "concur_worker_" + suffix);

            String title = "concurrency_" + suffix;
            jdbcTemplate.update("""
                insert into ticket
                    (creator_id, assignee_id, title, description, location, category, priority, status)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                    requesterId, workerId, title, "并发处理测试", "测试地点",
                    "NETWORK", "HIGH", "ASSIGNED");
            ticketId = jdbcTemplate.queryForObject(
                    "select id from ticket where creator_id = ? and title = ?",
                    Long.class, requesterId, title);
            Integer initialLogs = jdbcTemplate.queryForObject(
                    "select count(*) from ticket_log where ticket_id = ?",
                    Integer.class, ticketId);
            assertEquals(0, initialLogs.intValue());
            //开始两个线程
            CountDownLatch ready=new CountDownLatch(2);
            CountDownLatch start=new CountDownLatch(1);
            Long finalTicketId = ticketId;
            Long finalWorkerId = workerId;
            Callable<String> task=()->{
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                try{
                    ticketService.startProcessing(finalTicketId, finalWorkerId);
                    return "SUCCESS";
                }catch (BusinessException e){
                    return "CONFLICT:"+e.getStatus().value();
                }
            };
            pool= Executors.newFixedThreadPool(2);
            Future<String> f1=pool.submit(task);
            Future<String> f2=pool.submit(task);
            assertTrue(ready.await(5,TimeUnit.SECONDS),"两个线程未全部就位");
            start.countDown();
            List<String> results=new ArrayList<>();
            results.add(f1.get(5,TimeUnit.SECONDS));
            results.add(f2.get(5,TimeUnit.SECONDS));

            long success = results.stream().filter("SUCCESS"::equals).count();
            long conflicts = results.stream().filter(r -> r.startsWith("CONFLICT:409")).count();
            assertEquals(1, success, "应该只有一个成功：" + results);
            assertEquals(1, conflicts, "应该只有一个 409：" + results);

            String status=jdbcTemplate.queryForObject(
                    "select status from ticket where id=?",String.class,ticketId
            );
            assertEquals("PROCESSING",status);

            Long assigneeId = jdbcTemplate.queryForObject(
                    "select assignee_id from ticket where id = ?", Long.class, ticketId);
            assertEquals(workerId, assigneeId);

            Integer processedLogs = jdbcTemplate.queryForObject("""
                select count(*) from ticket_log
                where ticket_id = ? and from_status = 'ASSIGNED' and to_status = 'PROCESSING'
                """, Integer.class, ticketId);
            assertEquals(1, processedLogs.intValue());
        }finally {
            if (pool != null) {
                pool.shutdownNow();
            }
            if (ticketId != null) {
                jdbcTemplate.update("delete from ticket_log where ticket_id = ?", ticketId);
                jdbcTemplate.update("delete from ticket where id = ?", ticketId);
            }
            if (workerId != null) {
                jdbcTemplate.update("delete from app_user where id = ?", workerId);
            }
            if (requesterId != null) {
                jdbcTemplate.update("delete from app_user where id = ?", requesterId);
            }
        }
    }
}
