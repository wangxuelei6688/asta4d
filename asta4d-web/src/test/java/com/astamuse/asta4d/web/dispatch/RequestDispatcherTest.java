package com.astamuse.asta4d.web.dispatch;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.astamuse.asta4d.Context;
import com.astamuse.asta4d.template.TemplateResolver;
import com.astamuse.asta4d.web.WebApplicationConfiguration;
import com.astamuse.asta4d.web.WebApplicationContext;
import com.astamuse.asta4d.web.WebPage;
import com.astamuse.asta4d.web.dispatch.annotation.ContentProvider;
import com.astamuse.asta4d.web.dispatch.mapping.ext.UrlMappingRuleHelper;
import com.astamuse.asta4d.web.dispatch.request.RequestHandler;
import com.astamuse.asta4d.web.dispatch.response.Asta4DPageWriter;
import com.astamuse.asta4d.web.dispatch.response.ContentWriter;
import com.astamuse.asta4d.web.dispatch.response.JsonWriter;
import com.astamuse.asta4d.web.dispatch.response.RedirectActionWriter;
import com.astamuse.asta4d.web.dispatch.response.provider.RedirectDescriptor;
import com.astamuse.asta4d.web.dispatch.response.provider.RestResult;

public class RequestDispatcherTest {

    private RequestDispatcher dispatcher = new RequestDispatcher();

    private WebApplicationConfiguration configuration = new WebApplicationConfiguration() {
        {
            setTemplateResolver(new TemplateResolver() {
                @Override
                public TemplateInfo loadResource(String path) {
                    return createTemplateInfo(path, new ByteArrayInputStream(path.getBytes()));
                }
            });
        }
    };

    @BeforeTest
    public void setConf() {
        WebApplicationContext context = new WebApplicationContext();
        Context.setCurrentThreadContext(context);
        context.setConfiguration(configuration);

        UrlMappingRuleHelper helper = new UrlMappingRuleHelper();
        initTestRules(helper);

        dispatcher.setRuleExtractor(new AntPathRuleExtractor());
        dispatcher.setRuleList(helper.getArrangedRuleList());

    }

    @BeforeMethod
    public void initContext() {
        Context context = Context.getCurrentThreadContext();
        if (context == null) {
            context = new WebApplicationContext();
            Context.setCurrentThreadContext(context);
        }
        context.clearSavedData();
        WebApplicationContext webContext = (WebApplicationContext) context;
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        webContext.setRequest(request);
        webContext.setResponse(response);
    }

    private void initTestRules(UrlMappingRuleHelper rules) {

        rules.addGlobalForward(NullPointerException.class, "/NullPointerException", 501);
        rules.addGlobalForward(Exception.class, "/Exception", 500);

        //@formatter:off
        
        rules.add("/index").forward("/index.html")
                           .forward(Throwable.class, "/error.html", 500);

        rules.add("/go-redirect").redirect("/go-redirect/ok");
        
        rules.add(HttpMethod.DELETE, "/restapi").handler(TestRestApiHandler.class).rest();
        
        rules.add("/getjson").json(TestJsonQuery.class);
        rules.add("/thrownep").handler(ThrowNEPHandler.class).forward("/thrownep");
        rules.add("/throwexception").handler(ThrowExceptionHandler.class).forward("/throwexception");
        
        rules.add("/**/*").forward("/notfound", 404);
      //@formatter:on
    }

    @DataProvider(name = "data")
    public Object[][] getTestData() throws Exception {
        /*
        return new Object[][] { { "get", "/index", new Asta4DPageProvider("/index.html") },
                { new ReturnStringHandler(), new RequestHandlerInterceptor[0], "/test1.html" },
                { new ReturnDescriptorHandler(), new RequestHandlerInterceptor[0], "/test2.html" },
                { new ThrowDescriptorHandler(), new RequestHandlerInterceptor[0], "/test2.html" },
                { new VoidHandler(), new RequestHandlerInterceptor[] { new ViewChangeIntercepter() }, "/test4.html" },
                { new ThrowDescriptorHandler(), new RequestHandlerInterceptor[] { new CancelExceptionIntercepter() }, null } };
                */
        //@formatter:off
        return new Object[][] { 
                { "get", "/index", 0, new WebPage("/index.html"), new Asta4DPageWriter() },
                { "get", "/go-redirect", 0, new RedirectDescriptor("/go-redirect/ok", null), new RedirectActionWriter() },
                { "delete", "/restapi", 401, null, null }, 
                { "get", "/getjson", 0, new TestJsonObject(123), new JsonWriter() },
                { "get", "/nofile", 404, new WebPage("/notfound"), new Asta4DPageWriter() },
                { "get", "/thrownep", 501, new WebPage("/NullPointerException"), new Asta4DPageWriter() },
                { "get", "/throwexception", 500, new WebPage("/Exception"), new Asta4DPageWriter() },
                };
        //@formatter:on
    }

    @Test(dataProvider = "data")
    public void execute(String method, String url, int status, Object expectedContent, ContentWriter cw) throws Exception {
        WebApplicationContext context = (WebApplicationContext) Context.getCurrentThreadContext();
        HttpServletRequest request = context.getRequest();
        HttpServletResponse response = context.getResponse();
        HttpSession session = mock(HttpSession.class);

        when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
        when(request.getCookies()).thenReturn(new Cookie[0]);
        when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());
        when(request.getSession(true)).thenReturn(session);

        when(request.getRequestURI()).thenReturn(url);
        when(request.getContextPath()).thenReturn("");
        when(request.getMethod()).thenReturn(method);

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
            @Override
            public void write(int b) throws IOException {
                bos.write(b);
            }
        });
        dispatcher.dispatchAndProcess(request, response);

        if (status != 0) {
            verify(response).setStatus(status);
        }

        if (expectedContent == null) {
            return;
        }

        final ByteArrayOutputStream expectedBos = new ByteArrayOutputStream();
        HttpServletResponse expectedResponse = mock(HttpServletResponse.class);
        when(expectedResponse.getOutputStream()).thenReturn(new ServletOutputStream() {
            @Override
            public void write(int b) throws IOException {
                expectedBos.write(b);
            }
        });
        if (expectedContent instanceof RedirectDescriptor) {
            // how test?
        } else {
            cw.writeResponse(expectedResponse, expectedContent);

            Assert.assertEquals(new String(bos.toByteArray()), new String(expectedBos.toByteArray()));

        }
    }

    public static class TestRestApiHandler {

        @RequestHandler
        public RestResult doDelete() {
            return new RestResult(401);
        }
    }

    public static class TestJsonObject {
        private int value = 0;

        public TestJsonObject(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }

    public static class TestJsonQuery {

        @ContentProvider
        public TestJsonObject query() {
            TestJsonObject obj = new TestJsonObject(123);
            return obj;
        }
    }

    public static class ThrowNEPHandler {
        @RequestHandler
        public Object foo() {
            throw new NullPointerException();
        }
    }

    public static class ThrowExceptionHandler {
        @RequestHandler
        public Object foo() {
            throw new RuntimeException();
        }
    }

    /*
        private void assertContentProvider(ContentProvider result, ContentProvider expected) {
            if (expected instanceof Asta4DPageProvider) {
                Assert.assertEquals(((Asta4DPageProvider) result).getPath(), ((Asta4DPageProvider) expected).getPath());
            } else if (expected instanceof RedirectTargetProvider) {
                Assert.assertEquals(((RedirectTargetProvider) result).getUrl(), ((RedirectTargetProvider) expected).getUrl());
            } else {
                throw new UnsupportedOperationException(expected.getClass().toString());
            }
        }

        private UrlMappingRule getRule(Object... handlers) {
            UrlMappingRule rule = new UrlMappingRule();
            rule.setHandlerList(Arrays.asList(handlers));
            Map<Class<? extends ForwardDescriptor>, String> forwardDescriptors = new HashMap<>();
            forwardDescriptors.put(TestDescriptor.class, "/test2.html");
            rule.setForwardDescriptorMap(forwardDescriptors);
            return rule;
        }

        private static RequestHandlerInvoker getInvoker() {
            RequestHandlerInvokerFactory factory = new DefaultRequestHandlerInvokerFactory();
            return factory.getInvoker();
        }

        private abstract static class ExecutedCheckHandler {
            boolean executed = false;

            public void execute() {
                executed = true;
            }

            public boolean isExecuted() {
                return executed;
            }
        }

        private static class VoidHandler extends ExecutedCheckHandler {
            @RequestHandler
            public void handle() {
                super.execute();
            }
        }

        private static class ReturnStringHandler extends ExecutedCheckHandler {
            @RequestHandler
            public String handle() {
                super.execute();
                return "/test1.html";
            }
        }

        private static class ReturnDescriptorHandler extends ExecutedCheckHandler {
            @RequestHandler
            public ForwardDescriptor handle() {
                super.execute();
                return new TestDescriptor();
            }
        }

        private static class ThrowDescriptorHandler extends ExecutedCheckHandler {
            @RequestHandler
            public void handle() {
                super.execute();
                throw new ForwardableException(new TestDescriptor(), new IllegalArgumentException());
            }
        }

        private static class TestDescriptor implements ForwardDescriptor {

        }

        private static class ViewChangeDescriptor implements ForwardDescriptor {

        }

        private static class ViewChangeIntercepter implements RequestHandlerInterceptor {
            @Override
            public void preHandle(UrlMappingRule rule, RequestHandlerResultHolder holder) {
            }

            @Override
            public void postHandle(UrlMappingRule rule, RequestHandlerResultHolder holder, ExceptionHandler exceptionHandler) {
                holder.setForwardDescriptor(new ViewChangeDescriptor());
            }
        }

        private static class CancelExceptionIntercepter implements RequestHandlerInterceptor {
            @Override
            public void preHandle(UrlMappingRule rule, RequestHandlerResultHolder holder) {
            }

            @Override
            public void postHandle(UrlMappingRule rule, RequestHandlerResultHolder holder, ExceptionHandler exceptionHandler) {
                exceptionHandler.setException(null);
            }

        }
        */
}
