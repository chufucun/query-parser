package io.github.hive.util;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.github.hive.parser.HiveParse;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.calcite.sql.SqlBasicCall;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlInsert;
import org.apache.calcite.sql.SqlJoin;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlOrderBy;
import org.apache.calcite.sql.SqlSelect;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.hadoop.hive.ql.parse.ASTNode;
import org.apache.hadoop.hive.ql.parse.ParseDriver;
import org.apache.hadoop.hive.ql.parse.ParseException;

/**
 * Sql related utils.
 */
public class SqlUtil {

  /**
   * Parse table names.
   *
   * @param sql sql string
   * @return table names
   */
  public static HiveParse parse(String sql) {
    try {
      HiveParse hp = new HiveParse();
      hp.parse(sql);
      return hp;
    } catch (ParseException ex) {
      throw new RuntimeException(ex.getMessage());
    }
  }
}