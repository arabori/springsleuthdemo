package br.com.veltec;


import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.cloud.netflix.ribbon.RibbonClient;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by ryarabori on 26/04/16.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = { FeignClientServerErrorTest.TestConfiguration.class })
@WebIntegrationTest(value = { "spring.application.name=fooservice" }, randomPort = true)
public class FeignClientServerErrorTest {

    @Autowired
    private TestFeignInterface feignInterface;

    @Test
    public void shouldCloseSpanOnInternalServerError() {
        try {
            this.feignInterface.internalError();
        } catch (HystrixRuntimeException e) {
        }
    }

    @Test
    public void shouldCloseSpanOnNotFound() {
        try {
            this.feignInterface.notFound();
        } catch (HystrixRuntimeException e) {
        }
    }

    @Configuration
    @EnableAutoConfiguration
    @EnableFeignClients
    @RibbonClient(value = "fooservice", configuration = SimpleRibbonClientConfiguration.class)
    public static class TestConfiguration {

        @Bean
        FooController fooController() {
            return new FooController();
        }

        @Bean
        Listener listener() {
            return new Listener();
        }

        @LoadBalanced
        @Bean
        public RestTemplate restTemplate() {
            return new RestTemplate();
        }

    }

    @FeignClient(value = "fooservice")
    public interface TestFeignInterface {

        @RequestMapping(method = RequestMethod.GET, value = "/internalerror")
        ResponseEntity<String> internalError();

        @RequestMapping(method = RequestMethod.GET, value = "/notfound")
        ResponseEntity<String> notFound();

    }

    @Component
    public static class Listener implements SpanReporter {
        private List<Span> events = new ArrayList<>();

        public List<Span> getEvents() {
            return this.events;
        }

        @Override
        public void report(Span span) {
            this.events.add(span);
        }
    }

    @RestController
    public static class FooController {

        @Autowired
        Tracer tracer;

        @RequestMapping("/internalerror")
        public ResponseEntity<String> internalError(@RequestHeader(Span.TRACE_ID_NAME) String traceId,
                @RequestHeader(Span.SPAN_ID_NAME) String spanId,
                @RequestHeader(Span.PARENT_ID_NAME) String parentId) {
            return new ResponseEntity<>("internal error", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        @RequestMapping("/notfound")
        public ResponseEntity<String> notFound(@RequestHeader(Span.TRACE_ID_NAME) String traceId,
                @RequestHeader(Span.SPAN_ID_NAME) String spanId,
                @RequestHeader(Span.PARENT_ID_NAME) String parentId) {
            return new ResponseEntity<>("not found", HttpStatus.NOT_FOUND);
        }
    }

    @Configuration
    public static class SimpleRibbonClientConfiguration {

        @Value("${local.server.port}")
        private int port = 0;

        @Bean
        public ILoadBalancer ribbonLoadBalancer() {
            BaseLoadBalancer balancer = new BaseLoadBalancer();
            balancer.setServersList(Collections.singletonList(new Server("localhost", this.port)));
            return balancer;
        }
    }

}
