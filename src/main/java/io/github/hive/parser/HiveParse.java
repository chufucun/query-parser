package io.github.hive.parser;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.hive.ql.parse.ASTNode;
import org.apache.hadoop.hive.ql.parse.BaseSemanticAnalyzer;
import org.apache.hadoop.hive.ql.parse.HiveParser;
import org.apache.hadoop.hive.ql.parse.ParseDriver;
import org.apache.hadoop.hive.ql.parse.ParseException;

/**
 * source； https://www.bbsmax.com/A/n2d99GZYdD/
 * <p>
 * 目的：获取AST中的表，列，以及对其所做的操作，如SELECT,INSERT 重点：获取SELECT操作中的表和列的相关操作。其他操作这判断到表级别。
 * 实现思路：对AST深度优先遍历，遇到操作的token则判断当前的操作， 遇到TOK_TAB或TOK_TABREF则判断出当前操作的表，遇到子句则压栈当前处理，处理子句。 子句处理完，栈弹出。
 */
public class HiveParse {

  private static final String UNKNOWN = "UNKNOWN";
  private Map<String, String> alias = Maps.newHashMap();
  private Map<String, String> cols = Maps.newTreeMap();
  private Map<String, String> colAlais = Maps.newTreeMap();
  private Set<String> tables = Sets.newHashSet();
  private Stack<String> tableNameStack = new Stack<>();
  private Stack<Oper> operStack = new Stack<>();
  private String nowQueryTable = "";//定义及处理不清晰，修改为query或from节点对应的table集合或许好点。目前正在查询处理的表可能不止一个。
  private Oper oper;
  private boolean joinClause = false;

  private enum Oper {
    SELECT, INSERT, DROP, TRUNCATE, LOAD, CREATETABLE, ALTER
  }

  public Set<String> parseIteral(ASTNode ast) {
    Set<String> set = Sets.newHashSet();//当前查询所对应到的表集合
    prepareToParseCurrentNodeAndChilds(ast);
    set.addAll(parseChildNodes(ast));
    set.addAll(parseCurrentNode(ast, set));
    endParseCurrentNode(ast);
    return set;
  }

  private void endParseCurrentNode(ASTNode ast) {
    if (ast.getToken() != null) {
      switch (ast.getToken().getType()) {//join 从句结束，跳出join
        case HiveParser.TOK_RIGHTOUTERJOIN:
        case HiveParser.TOK_LEFTOUTERJOIN:
        case HiveParser.TOK_JOIN:
          joinClause = false;
          break;
        case HiveParser.TOK_QUERY:
          break;
        case HiveParser.TOK_INSERT:
        case HiveParser.TOK_SELECT:
          nowQueryTable = tableNameStack.pop();
          oper = operStack.pop();
          break;
        default:
      }
    }
  }

  private Set<String> parseCurrentNode(ASTNode ast, Set<String> set) {
    if (ast.getToken() != null) {
      switch (ast.getToken().getType()) {
        case HiveParser.TOK_TABLE_PARTITION:
//            case HiveParser.TOK_TABNAME:
          if (ast.getChildCount() != 2) {
            String table = BaseSemanticAnalyzer
                .getUnescapedName((ASTNode) ast.getChild(0));
            if (oper == Oper.SELECT) {
              nowQueryTable = table;
            }
            tables.add(table + "\t" + oper);
          }
          break;

        case HiveParser.TOK_TAB:// outputTable
          String tableTab = BaseSemanticAnalyzer
              .getUnescapedName((ASTNode) ast.getChild(0));
          if (oper == Oper.SELECT) {
            nowQueryTable = tableTab;
          }
          tables.add(tableTab + "\t" + oper);
          break;
        case HiveParser.TOK_TABREF:// inputTable
          ASTNode tabTree = (ASTNode) ast.getChild(0);
          String tableName = (tabTree.getChildCount() == 1) ? BaseSemanticAnalyzer
              .getUnescapedName((ASTNode) tabTree.getChild(0))
              : BaseSemanticAnalyzer
                  .getUnescapedName((ASTNode) tabTree.getChild(0))
                  + "." + tabTree.getChild(1);
          if (oper == Oper.SELECT) {
            if (joinClause && !"".equals(nowQueryTable)) {
              nowQueryTable += "&" + tableName;//
            } else {
              nowQueryTable = tableName;
            }
            set.add(tableName);
          }
          tables.add(tableName + "\t" + oper);
          if (ast.getChild(1) != null) {
            String alia = ast.getChild(1).getText().toLowerCase();
            alias.put(alia, tableName);//sql6 p别名在tabref只对应为一个表的别名。
          }
          break;
        case HiveParser.TOK_TABLE_OR_COL:
          if (ast.getParent().getType() != HiveParser.DOT) {
            String col = ast.getChild(0).getText().toLowerCase();
            if (alias.get(col) == null
                && colAlais.get(nowQueryTable + "." + col) == null) {
              if (nowQueryTable.indexOf("&") > 0) {//sql23
                cols.put(UNKNOWN + "." + col, "");
              } else {
                cols.put(nowQueryTable + "." + col, "");
              }
            }
          }
          break;
        case HiveParser.TOK_ALLCOLREF:
          cols.put(nowQueryTable + ".*", "");
          break;
        case HiveParser.TOK_SUBQUERY:
          if (ast.getChildCount() == 2) {
            String tableAlias = unescapeIdentifier(ast.getChild(1)
                .getText());
            String aliaReal = "";
            for (String table : set) {
              aliaReal += table + "&";
            }
            if (aliaReal.length() != 0) {
              aliaReal = aliaReal.substring(0, aliaReal.length() - 1);
            }
//                    alias.put(tableAlias, nowQueryTable);//sql22
            alias.put(tableAlias, aliaReal);//sql6
//                    alias.put(tableAlias, "");// just store alias
          }
          break;
        case HiveParser.TOK_SELEXPR:
          if (ast.getChild(0).getType() == HiveParser.TOK_TABLE_OR_COL) {
            String column = ast.getChild(0).getChild(0).getText()
                .toLowerCase();
            if (nowQueryTable.indexOf("&") > 0) {
              cols.put(UNKNOWN + "." + column, "");
            } else if (colAlais.get(nowQueryTable + "." + column) == null) {
              cols.put(nowQueryTable + "." + column, "");
            }
          } else if (ast.getChild(1) != null) {// TOK_SELEXPR (+
            // (TOK_TABLE_OR_COL id)
            // 1) dd
            String columnAlia = ast.getChild(1).getText().toLowerCase();
            colAlais.put(nowQueryTable + "." + columnAlia, "");
          }
          break;
        case HiveParser.DOT:
          if (ast.getType() == HiveParser.DOT) {
            if (ast.getChildCount() == 2) {
              if (ast.getChild(0).getType() == HiveParser.TOK_TABLE_OR_COL
                  && ast.getChild(0).getChildCount() == 1
                  && ast.getChild(1).getType() == HiveParser.Identifier) {
                String alia = BaseSemanticAnalyzer
                    .unescapeIdentifier(ast.getChild(0)
                        .getChild(0).getText()
                        .toLowerCase());
                String column = BaseSemanticAnalyzer
                    .unescapeIdentifier(ast.getChild(1)
                        .getText().toLowerCase());
                String realTable = null;
                if (!tables.contains(alia + "\t" + oper)
                    && alias.get(alia) == null) {// [b SELECT, a
                  // SELECT]
                  alias.put(alia, nowQueryTable);
                }
                if (tables.contains(alia + "\t" + oper)) {
                  realTable = alia;
                } else if (alias.get(alia) != null) {
                  realTable = alias.get(alia);
                }
                if (realTable == null || realTable.length() == 0 || realTable.indexOf("&") > 0) {
                  realTable = UNKNOWN;
                }
                cols.put(realTable + "." + column, "");

              }
            }
          }
          break;
        case HiveParser.TOK_ALTERTABLE_ADDPARTS:
        case HiveParser.TOK_ALTERTABLE_RENAME:
        case HiveParser.TOK_ALTERTABLE_ADDCOLS:
          ASTNode alterTableName = (ASTNode) ast.getChild(0);
          tables.add(alterTableName.getText() + "\t" + oper);
          break;
        default:
      }
    }
    return set;
  }

  private Set<String> parseChildNodes(ASTNode ast) {
    Set<String> set = Sets.newHashSet();
    int numCh = ast.getChildCount();
    if (numCh > 0) {
      for (int num = 0; num < numCh; num++) {
        ASTNode child = (ASTNode) ast.getChild(num);
        set.addAll(parseIteral(child));
      }
    }
    return set;
  }

  private void prepareToParseCurrentNodeAndChilds(ASTNode ast) {
    if (ast.getToken() != null) {
      switch (ast.getToken().getType()) {//join 从句开始
        case HiveParser.TOK_RIGHTOUTERJOIN:
        case HiveParser.TOK_LEFTOUTERJOIN:
        case HiveParser.TOK_JOIN:
          joinClause = true;
          break;
        case HiveParser.TOK_QUERY:
          tableNameStack.push(nowQueryTable);
          operStack.push(oper);
          nowQueryTable = "";//sql22
          oper = Oper.SELECT;
          break;
        case HiveParser.TOK_INSERT:
          tableNameStack.push(nowQueryTable);
          operStack.push(oper);
          oper = Oper.INSERT;
          break;
        case HiveParser.TOK_SELECT:
          tableNameStack.push(nowQueryTable);
          operStack.push(oper);
//                    nowQueryTable = nowQueryTable
          // nowQueryTable = "";//语法树join
          // 注释语法树sql9， 语法树join对应的设置为""的注释逻辑不符
          oper = Oper.SELECT;
          break;
        case HiveParser.TOK_DROPTABLE:
          oper = Oper.DROP;
          break;
        case HiveParser.TOK_TRUNCATETABLE:
          oper = Oper.TRUNCATE;
          break;
        case HiveParser.TOK_LOAD:
          oper = Oper.LOAD;
          break;
        case HiveParser.TOK_CREATETABLE:
          oper = Oper.CREATETABLE;
          break;
        default:
      }
      if (ast.getToken() != null
          && ast.getToken().getType() >= HiveParser.TOK_ALTERDATABASE_PROPERTIES
          && ast.getToken().getType() <= HiveParser.TOK_ALTERVIEW_RENAME) {
        oper = Oper.ALTER;
      }
    }
  }

  public static String unescapeIdentifier(String val) {
    if (val == null) {
      return null;
    }
    if (val.charAt(0) == '`' && val.charAt(val.length() - 1) == '`') {
      val = val.substring(1, val.length() - 1);
    }
    return val;
  }

  public void parse(String sql) throws ParseException {
    ParseDriver parseDriver = new ParseDriver();
    ASTNode astNode = parseDriver.parse(sql);
    parseIteral(astNode);
  }

  public void parse(ASTNode ast) {
    parseIteral(ast);
    println();
  }

  public void println() {
    System.out.println("Tables:");
    tables.forEach(r -> System.out.println(String.format("\t%s", r)));

    System.out.println("Columns:");
    cols.forEach((k, v) -> {
      System.out.println(String.format("\t%s\t%s", k, v));
    });
    System.out.println("Alias:");
    alias.forEach((k, v) -> {
      System.out.println(String.format("\t%s\t%s", k, v));
    });
  }

  public Map<String, String> getAlias() {
    return alias;
  }

  public Map<String, String> getCols() {
    return cols;
  }

  public Set<String> getTables() {
    return tables;
  }
}