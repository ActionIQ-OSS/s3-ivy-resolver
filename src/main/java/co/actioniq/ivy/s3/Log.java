package co.actioniq.ivy.s3;

import org.apache.ivy.util.Message;
import org.apache.ivy.util.StringUtils;

public class Log {
  private static final String PREFIX = "AIQ S3";

  private Log() {}

  static void dumpStack() {
    try {
      throw new IllegalArgumentException();
    } catch (IllegalArgumentException t) {
      Message.debug(StringUtils.getStackTrace(t));
    }
  }

  static void print(String msg) {
    Message.debug(PREFIX + " " +msg);
  }
}
