/**
 * Logback: the reliable, generic, fast and flexible logging framework.
 * Copyright (C) 1999-2015, QOS.ch. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation.
 */
package ch.qos.logback.classic.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.ClassicTestConstants;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.net.mock.MockSyslogServer;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.net.SyslogConstants;
import ch.qos.logback.core.recovery.RecoveryCoordinator;
import ch.qos.logback.core.testUtil.RandomUtil;
import ch.qos.logback.core.util.StatusPrinter;

import java.nio.charset.Charset;
@Ignore
public class SyslogAppenderTest {

    private static final String SYSLOG_PREFIX_REGEX = "<\\d{2}>\\w{3} [\\d ]\\d \\d{2}(:\\d{2}){2} [\\w.-]* ";

    LoggerContext lc = new LoggerContext();
    SyslogAppender sa = new SyslogAppender();
    MockSyslogServer mockServer;
    String loggerName = this.getClass().getName();
    Logger logger = lc.getLogger(loggerName);

    @Before
    public void setUp() throws Exception {
        lc.setName("test");
        sa.setContext(lc);
    }

    @After
    public void tearDown() throws Exception {
    }

    public void setMockServerAndConfigure(int expectedCount, String suffixPattern) throws InterruptedException {
        int port = RandomUtil.getRandomServerPort();

        mockServer = new MockSyslogServer(expectedCount, port);
        mockServer.start();
        // give MockSyslogServer head start

        Thread.sleep(100);

        sa.setSyslogHost("localhost");
        sa.setFacility("MAIL");
        sa.setPort(port);
        sa.setSuffixPattern(suffixPattern);
        sa.setStackTracePattern("[%thread] foo " + CoreConstants.TAB);
        sa.start();
        assertTrue(sa.isStarted());

        String loggerName = this.getClass().getName();
        Logger logger = lc.getLogger(loggerName);
        logger.addAppender(sa);

    }

    public void setMockServerAndConfigure(int expectedCount) throws InterruptedException {
        this.setMockServerAndConfigure(expectedCount, "[%thread] %logger %msg");
    }

    @Test
    public void basic() throws InterruptedException {

        setMockServerAndConfigure(1);
        String logMsg = "hello";
        logger.debug(logMsg);

        // wait max 2 seconds for mock server to finish. However, it should
        // much sooner than that.
        mockServer.join(8000);

        assertTrue(mockServer.isFinished());
        assertEquals(1, mockServer.getMessageList().size());
        String msg = new String(mockServer.getMessageList().get(0));

        String threadName = Thread.currentThread().getName();

        String expected = "<" + (SyslogConstants.LOG_MAIL + SyslogConstants.DEBUG_SEVERITY) + ">";
        assertTrue(msg.startsWith(expected));

        checkRegexMatch(msg, SYSLOG_PREFIX_REGEX + "\\[" + threadName + "\\] " + loggerName + " " + logMsg);

    }

    @Test
    public void suffixPatternWithTag() throws InterruptedException {
        setMockServerAndConfigure(1, "test/something [%thread] %logger %msg");
        String logMsg = "hello";
        logger.debug(logMsg);

        // wait max 2 seconds for mock server to finish. However, it should
        // much sooner than that.
        mockServer.join(8000);

        assertTrue(mockServer.isFinished());
        assertEquals(1, mockServer.getMessageList().size());
        String msg = new String(mockServer.getMessageList().get(0));

        String threadName = Thread.currentThread().getName();

        String expected = "<" + (SyslogConstants.LOG_MAIL + SyslogConstants.DEBUG_SEVERITY) + ">";
        assertTrue(msg.startsWith(expected));

        checkRegexMatch(msg, SYSLOG_PREFIX_REGEX + "test/something \\[" + threadName + "\\] " + loggerName + " " + logMsg);

    }

    @Test
    public void tException() throws InterruptedException {
        setMockServerAndConfigure(21);

        String logMsg = "hello";
        String exMsg = "just testing";
        Exception ex = new Exception(exMsg);
        logger.debug(logMsg, ex);
        StatusPrinter.print(lc);

        // wait max 2 seconds for mock server to finish. However, it should
        // much sooner than that.
        mockServer.join(8000);
        assertTrue(mockServer.isFinished());

        // message + 20 lines of stacktrace
        assertEquals(21, mockServer.getMessageList().size());
        // int i = 0;
        // for (String line: mockServer.msgList) {
        // System.out.println(i++ + ": " + line);
        // }

        String msg = new String(mockServer.getMessageList().get(0));
        String expected = "<" + (SyslogConstants.LOG_MAIL + SyslogConstants.DEBUG_SEVERITY) + ">";
        assertTrue(msg.startsWith(expected));

        String threadName = Thread.currentThread().getName();
        String regex = SYSLOG_PREFIX_REGEX + "\\[" + threadName + "\\] " + loggerName + " " + logMsg;
        checkRegexMatch(msg, regex);

        msg = new String(mockServer.getMessageList().get(1));
        assertTrue(msg.contains(ex.getClass().getName()));
        assertTrue(msg.contains(ex.getMessage()));

        msg = new String(mockServer.getMessageList().get(2));
        assertTrue(msg.startsWith(expected));
        regex = SYSLOG_PREFIX_REGEX + "\\[" + threadName + "\\] " + "foo " + CoreConstants.TAB + "at ch\\.qos.*";
        checkRegexMatch(msg, regex);
    }

    @Test
    public void causedBy() throws InterruptedException {
        setMockServerAndConfigure(53);

        String logMsg = "hello";

        String causeMsg = "causing exception";
        Exception cause = new Exception(causeMsg);
        String exMsg = "just testing";
        Exception ex = new Exception(exMsg, cause);
        logger.debug(logMsg, ex);
        StatusPrinter.print(lc);

        // wait max 8 seconds for mock server to finish. However, it should
        // much sooner than that.
        mockServer.join(8000);
        assertTrue(mockServer.isFinished());

        // 2 messages + 51 lines of stacktrace
        assertEquals(53, mockServer.getMessageList().size());

        String msg = new String(mockServer.getMessageList().get(27));
        assertTrue(msg.contains(cause.getClass().getName()));
        assertTrue(msg.contains(cause.getMessage()));
    }

    @Test
    public void suppressed() throws InterruptedException {
        setMockServerAndConfigure(79);

        String logMsg = "hello";

        String exMsg = "just testing";
        Exception ex = new Exception(exMsg);

        String suppressedMsg1 = "suppressed exception 1";
        Exception suppressed1 = new Exception(suppressedMsg1);
        String suppressedMsg2 = "suppressed exception 2";
        Exception suppressed2 = new Exception(suppressedMsg2);
        ex.addSuppressed(suppressed1);
        ex.addSuppressed(suppressed2);
        logger.debug(logMsg, ex);
        StatusPrinter.print(lc);

        // wait max 8 seconds for mock server to finish. However, it should
        // much sooner than that.
        mockServer.join(8000);
        assertTrue(mockServer.isFinished());

        // 3 messages + 76 lines of stacktrace
        assertEquals(79, mockServer.getMessageList().size());

        String msg = new String(mockServer.getMessageList().get(27));
        assertTrue(msg.contains(suppressed1.getClass().getName()));
        assertTrue(msg.contains(suppressed1.getMessage()));

        msg = new String(mockServer.getMessageList().get(53));
        assertTrue(msg.contains(suppressed2.getClass().getName()));
        assertTrue(msg.contains(suppressed2.getMessage()));
    }

    @Test
    public void nestedCauseAndSuppressed() throws InterruptedException {
        setMockServerAndConfigure(130);

        String logMsg = "hello";

        String causeMsg1 = "causing exception 1";
        Exception cause1 = new Exception(causeMsg1);
        String exMsg = "just testing";
        Exception ex = new Exception(exMsg, cause1);

        String causeMsg2 = "causing exception 2";
        Exception cause2 = new Exception(causeMsg2);
        String suppressedMsg1 = "suppressed exception 1";
        Exception suppressed1 = new Exception(suppressedMsg1, cause2);

        String suppressedMsg2 = "suppressed exception 2";
        Exception suppressed2 = new Exception(suppressedMsg2);

        ex.addSuppressed(suppressed1);
        ex.addSuppressed(suppressed2);
        logger.debug(logMsg, ex);
        StatusPrinter.print(lc);

        // wait max 8 seconds for mock server to finish. However, it should
        // much sooner than that.
        mockServer.join(8000);
        int i = 0;
        for (byte[] line: mockServer.getMessageList()) {
            System.out.println(String.format("%03d", i++) + ": " + new String(line));
        }
        assertTrue(mockServer.isFinished());

        // 4 messages + 126 lines of stacktrace
        assertEquals(130, mockServer.getMessageList().size());

        String msg = new String(mockServer.getMessageList().get(27));
        assertTrue(msg.contains(suppressed1.getClass().getName()));
        assertTrue(msg.contains(suppressed1.getMessage()));

        msg = new String(mockServer.getMessageList().get(53));
        assertTrue(msg.contains(cause2.getClass().getName()));
        assertTrue(msg.contains(cause2.getMessage()));

        msg = new String(mockServer.getMessageList().get(79));
        assertTrue(msg.contains(suppressed2.getClass().getName()));
        assertTrue(msg.contains(suppressed2.getMessage()));

        msg = new String(mockServer.getMessageList().get(105));
        assertTrue(msg.contains(cause1.getClass().getName()));
        assertTrue(msg.contains(cause1.getMessage()));
    }

    private void checkRegexMatch(String s, String regex) {
        assertTrue("The string [" + s + "] did not match regex [" + regex + "]", s.matches(regex));
    }

    @Test
    public void large() throws Exception {
        setMockServerAndConfigure(2);
        StringBuilder largeBuf = new StringBuilder();
        for (int i = 0; i < 2 * 1024 * 1024; i++) {
            largeBuf.append('a');
        }
        logger.debug(largeBuf.toString());

        String logMsg = "hello";
        logger.debug(logMsg);
        Thread.sleep(RecoveryCoordinator.BACKOFF_COEFFICIENT_MIN + 10);
        logger.debug(logMsg);

        mockServer.join(8000);
        assertTrue(mockServer.isFinished());

        // both messages received
        assertEquals(2, mockServer.getMessageList().size());

        String expected = "<" + (SyslogConstants.LOG_MAIL + SyslogConstants.DEBUG_SEVERITY) + ">";
        String threadName = Thread.currentThread().getName();

        // large message is truncated
        final int maxMessageSize = sa.getMaxMessageSize();
        String largeMsg = new String(mockServer.getMessageList().get(0));
        assertTrue(largeMsg.startsWith(expected));
        String largeRegex = SYSLOG_PREFIX_REGEX + "\\[" + threadName + "\\] " + loggerName + " " + "a{" + (maxMessageSize - 2000) + "," + maxMessageSize + "}";
        checkRegexMatch(largeMsg, largeRegex);

        String msg = new String(mockServer.getMessageList().get(1));
        assertTrue(msg.startsWith(expected));
        String regex = SYSLOG_PREFIX_REGEX + "\\[" + threadName + "\\] " + loggerName + " " + logMsg;
        checkRegexMatch(msg, regex);
    }

    @Test
    public void LBCLASSIC_50() throws JoranException {

        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();

        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(lc);
        lc.reset();
        configurator.doConfigure(ClassicTestConstants.JORAN_INPUT_PREFIX + "syslog_LBCLASSIC_50.xml");

        org.slf4j.Logger logger = LoggerFactory.getLogger(this.getClass());
        logger.info("hello");
    }

    @Test
    public void unknownHostShouldNotCauseStopToFail() {
        // See LOGBACK-960
        sa.setSyslogHost("unknown.host");
        sa.setFacility("MAIL");
        sa.start();
        sa.stop();
    }

    @Test
    public void nonAsciiMessageEncoding() throws Exception {
        // See LOGBACK-732
        setMockServerAndConfigure(1);

        // Use a string that can be encoded in a somewhat odd encoding (ISO-8859-4) to minimize
        // the probability of the encoding test to work by accident
        String logMsg = "R\u0129ga"; // Riga spelled with the i having a tilda on top

        Charset ISO_8859_4 = Charset.forName("ISO-8859-4");
        sa.setCharset(ISO_8859_4);
        logger.debug(logMsg);

        // wait max 8 seconds for mock server to finish. However, it should
        // be done much sooner than that.
        mockServer.join(8000);

        assertTrue(mockServer.isFinished());
        assertEquals(1, mockServer.getMessageList().size());
        String msg = new String(mockServer.getMessageList().get(0), ISO_8859_4);
        String threadName = Thread.currentThread().getName();

        String expected = "<" + (SyslogConstants.LOG_MAIL + SyslogConstants.DEBUG_SEVERITY) + ">";
        assertTrue(msg.startsWith(expected));

        System.out.println(logMsg);
        checkRegexMatch(msg, SYSLOG_PREFIX_REGEX + "\\[" + threadName + "\\] " + loggerName + " " + logMsg);

    }
}
