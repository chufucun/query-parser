package io.github.hive;

import io.github.hive.parser.HiveParse;
import io.github.hive.util.SqlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HiveParserApplication implements CommandLineRunner {

  private static Logger LOG = LoggerFactory.getLogger(HiveParserApplication.class);

  public static void main(String[] args) {
    SpringApplication.run(HiveParserApplication.class, args);
  }

  @Override
  public void run(String... args) throws Exception {

    if (args.length != 1) {
      System.out.println("Usage: ./queryparser-xxx.jar \"<SQL>\"");
      System.exit(1);
    }
    String sql = args[0];
    System.out.println("Input : " + sql);
    System.out.println("Output: ");
    // 解析sql
    HiveParse parse = SqlUtil.parse(sql);
    // 打印
    parse.println();
  }
}
