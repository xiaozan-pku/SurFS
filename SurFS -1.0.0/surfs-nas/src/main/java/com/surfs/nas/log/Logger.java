/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package com.surfs.nas.log;

import com.surfs.nas.transport.ThreadPool;
import com.surfs.nas.util.TextUtils;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.Date;
import java.util.regex.Matcher;
import org.apache.log4j.Priority;


public final class Logger extends AbstractLogger {

    static boolean writeLog4j = false;
    private LogProperties properties = null;
    private Writer writer = null;
    private String logPath = null;
    private org.apache.log4j.Logger log4j = null;

    /**
     *
     * @param properties LogProperties
     */
    protected Logger(LogProperties properties) {
        String path = LogFactory.logPath;
        if (path != null) {
            path = path.endsWith(File.separator) ? path + properties.getLogName() : path + File.separator + properties.getLogName();
            File tmpFile = new File(path);
            if (!tmpFile.exists()) {
                tmpFile.mkdirs();
            }
            this.logPath = path + File.separator;
        }
        this.properties = properties;
        this.writer = new Writer(logPath);
    }

    /**
     *
     * @param log
     */
    private Logger(Logger log) {
        this.properties = log.properties;
        this.logPath = log.logPath;
        this.writer = log.writer;
    }

    /**
     *
     * @param caller Class 
     * @return Logger
     */
    public Logger getLogger(Class caller) {
        if (caller == null) {
            return getLogger(Thread.currentThread().getClass().getName());
        } else {
            return getLogger(caller.getName());
        }
    }

    /**
     *
     * @param caller
     * @return Logger
     */
    public Logger getLogger(String caller) {
        Logger log = new Logger(this);
        if (caller == null) {
            log.className = Thread.currentThread().getClass().getName();
        } else {
            log.className = caller;
        }
        log.log4j = org.apache.log4j.Logger.getLogger(log.className);
        return log;
    }

    /**
     *
     * @param strLine String
     * @param callclass Class
     * @param level String
     */
    private String makeString(String strLine, String callclass, Level level) {
        if (strLine == null) {
            strLine = "[null]";
        }
        StringBuilder sb = new StringBuilder();
        if (!properties.getDateformatter().isEmpty()) {
            sb.append(TextUtils.Date2String(new Date(), getProperties().getDateformatter()));
        } else {
            if (level == Level.FATAL) {
                sb.append(TextUtils.Date2String(new Date(), "MM-dd HH:mm:ss"));
            }
        }
        if (getProperties().isAddClassName()) {
            if (callclass == null || callclass.isEmpty()) {
                sb.append("[").append(Thread.currentThread().getName()).append("]");
            } else {
                sb.append("[").append(callclass).append("]");
            }
        }

        if (getProperties().isAddLevel() || level == Level.FATAL) {
            sb.append("[").append(level.toString()).append("]");
        }
        sb.append(strLine).append("\r\n");
        return sb.toString();
    }
    private static ThreadLocal<Logger> threadLogger = new ThreadLocal<>();

    /**
     *
     * @return boolean
     */
    protected static boolean isWrap() {
        return threadLogger.get() != null;
    }

    /**
     *
     * @param ss
     * @param level
     */
    private void writeLog(String ss, Level level) {
        if (getProperties().isOutConsole()) {
            threadLogger.set(this);
            System.out.print(ss);
            threadLogger.remove();
        }
        getWriter().write(ss);
        if (level == Level.FATAL && (!properties.getLogName().equals(LogFactory.SYSTEM_LOGNAME))) {
            LogFactory.getLogger(LogFactory.SYSTEM_LOGNAME).writer.write(ss);
        }
    }

    /**
     *
     * @param strLine
     * @param classname
     * @param level
     */
    private void filter(String strLine, String classname, Level level) {
        String ss = makeString(strLine, classname, level);
        Matcher matcher = getProperties().getFilter().matcher(ss);
        if (matcher.find()) {
            writeLog4j(strLine, classname, level, null);
            writeLog(ss, level);
        }
    }

    /**
     *
     * @param cmd WarnCommand
     */
    private synchronized void executewarn(String strLine, String classname) {
        if (getProperties().getWarnInteral() <= 0
                || System.currentTimeMillis() - writer.getLastwarntime() > (getProperties().getWarnInteral() * 1000)) {
            writer.setLastwarntime(System.currentTimeMillis());
            WarnExecuter warn = new WarnExecuter(new WarnCommand(this, strLine, classname, new Date()));
            ThreadPool.pool.execute(warn);
        }
    }

    /**
     *
     * @param strLine
     * @param classname
     * @param level
     * @param t
     */
    private void writeLog4j(String strLine, String classname, Level level, Throwable t) {
        if (!writeLog4j) {
            return;
        }
        Priority level4j;
        if (level == Level.DEBUG) {
            level4j = org.apache.log4j.Level.DEBUG;
        } else if (level == Level.INFO) {
            level4j = org.apache.log4j.Level.INFO;
        } else if (level == Level.WARN) {
            level4j = org.apache.log4j.Level.WARN;
        } else if (level == Level.FATAL) {
            level4j = org.apache.log4j.Level.FATAL;
        } else {
            level4j = org.apache.log4j.Level.ERROR;
        }
        if (log4j != null) {
            if (t == null) {
                log4j.log(level4j, strLine);
            } else {
                log4j.log(level4j, strLine, t);
            }
        } else {
            if (t == null) {
                org.apache.log4j.Logger.getLogger(classname).log(level4j, classname);
            } else {
                org.apache.log4j.Logger.getLogger(classname).log(level4j, classname, t);
            }
        }
    }

    /**
     *
     * @param strLine 
     * @param classname 
     */
    @Override
    public void fatal(String strLine, String classname) {
        writeLog4j(strLine, classname, Level.FATAL, null);
        writeLog(makeString(strLine, classname, Level.FATAL), Level.FATAL);
        executewarn(strLine, classname);
    }

    /**
     *
     * @param pattern
     * @param args
     * @param classname
     */
    @Override
    public void error(String pattern, Object[] args, String classname) {
        if (getProperties().getLevel().intValue() <= Level.ERROR.intValue()) {
            String strLine = args == null || args.length == 0 ? pattern : MessageFormat.format(pattern, args);
            writeLog4j(strLine, classname, Level.ERROR, null);
            writeLog(makeString(strLine, classname, Level.ERROR), Level.ERROR);
        } else {
            if (getProperties().getFilter() != null) {
                filter(args == null || args.length == 0 ? pattern : MessageFormat.format(pattern, args), classname, Level.ERROR);
            }
        }
    }

    /**
     * @param pattern
     * @param args
     * @param classname
     */
    @Override
    public void warn(String pattern, Object[] args, String classname) {
        if (getProperties().getLevel().intValue() <= Level.WARN.intValue()) {
            String strLine = args == null || args.length == 0 ? pattern : MessageFormat.format(pattern, args);
            writeLog4j(strLine, classname, Level.WARN, null);
            writeLog(makeString(strLine, classname, Level.WARN), Level.WARN);
        } else {
            if (getProperties().getFilter() != null) {
                filter(args == null || args.length == 0 ? pattern : MessageFormat.format(pattern, args), classname, Level.WARN);
            }
        }
    }

    /**
     *
     * @param pattern
     * @param args
     * @param classname
     */
    @Override
    public void info(String pattern, Object[] args, String classname) {
        if (getProperties().getLevel().intValue() <= Level.INFO.intValue()) {
            String strLine = args == null || args.length == 0 ? pattern : MessageFormat.format(pattern, args);
            writeLog4j(strLine, classname, Level.INFO, null);
            writeLog(makeString(strLine, classname, Level.INFO), Level.INFO);
        } else {
            if (getProperties().getFilter() != null) {
                filter(args == null || args.length == 0 ? pattern : MessageFormat.format(pattern, args), classname, Level.INFO);
            }
        }
    }

    /**
     *
     * @param pattern
     * @param args
     * @param classname
     */
    @Override
    public void debug(String pattern, Object[] args, String classname) {
        if (getProperties().getLevel().intValue() <= Level.DEBUG.intValue()) {
            String strLine = args == null || args.length == 0 ? pattern : MessageFormat.format(pattern, args);
            writeLog4j(strLine, classname, Level.DEBUG, null);
            writeLog(makeString(strLine, classname, Level.DEBUG), Level.DEBUG);
        } else {
            if (getProperties().getFilter() != null) {
                filter(args == null || args.length == 0 ? pattern : MessageFormat.format(pattern, args), classname, Level.DEBUG);
            }
        }
    }

    /**
     *
     * @param strLine
     * @param t
     * @param classname
     */
    @Override
    public void trace(String strLine, Throwable t, String classname) {
        if (getProperties().getLevel().intValue() <= Level.ERROR.intValue()) {
            writeLog4j(strLine, classname, Level.ERROR, t);
            if (t != null) {
                StringWriter sw = new StringWriter(1024);
                sw.append(strLine);
                sw.append("\r\n");
                PrintWriter pw = new PrintWriter(sw);
                t.printStackTrace(pw);
                pw.close();
                writeLog(makeString(sw.toString(), classname, Level.TRACE), Level.TRACE);
            } else {
                writeLog(makeString(strLine, classname, Level.ERROR), Level.ERROR);
            }
        } else {
            if (getProperties().getFilter() != null) {
                if (t != null) {
                    StringWriter sw = new StringWriter(1024);
                    sw.append(strLine);
                    sw.append("\r\n");
                    PrintWriter pw = new PrintWriter(sw);
                    t.printStackTrace(pw);
                    pw.close();
                    filter(sw.toString(), classname, Level.TRACE);
                } else {
                    filter(strLine, classname, Level.ERROR);
                }
            }
        }
    }

    /**
     *
     * @param logLevel
     * @param pattern
     * @param args
     * @param callclass
     */
    @Override
    public void log(Level logLevel, String pattern, Object[] args, String callclass) {
        if (logLevel == Level.DEBUG) {
            debug(pattern, args, callclass);
        } else if (logLevel == Level.INFO) {
            info(pattern, args, callclass);
        } else if (logLevel == Level.WARN) {
            warn(pattern, args, callclass);
        } else if (logLevel == Level.ERROR) {
            error(pattern, args, callclass);
        } else {
            fatal(pattern, args, callclass);
        }
    }

    /**
     * @return the writer
     */
    public Writer getWriter() {
        return writer;
    }

    /**
     * @return the properties
     */
    public LogProperties getProperties() {
        return properties;
    }

    /**
     * @return the logPath
     */
    public String getLogPath() {
        return logPath;
    }

    /**
     * @param properties the properties to set
     */
    public void setProperties(LogProperties properties) {
        this.properties = properties;
    }

}
