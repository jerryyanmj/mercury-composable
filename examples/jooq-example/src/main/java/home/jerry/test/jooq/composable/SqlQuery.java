package home.jerry.test.jooq.composable;

import org.platformlambda.core.util.Utility;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SqlQuery implements Serializable {
    static final char[] SQL_CHAR_SEARCH = {'\''};
    static final String[] SQL_CHAR_REPLACE = {"\'\'"};

    public static final String ERROR_SET_FROM = "Please call setFrom(sql) before doing any joins.";

    public static final int BATCH_SIZE_DEFAULT = 0;
    public static final int BATCH_SIZE_INFINITE = -1;

    public static final String EQUAL_OPERATOR = "=";
    public static final String NOT_EQUAL_OPERATOR = "!=";
    public static final String GREATER_OPERATOR = ">";
    public static final String GREATER_EQUAL_OPERATOR = ">=";
    public static final String LESS_OPERATOR = "<";
    public static final String LESS_EQUAL_OPERATOR = "<=";
    public static final String LIKE_OPERATOR = " like ";
    public static final String IN_OPERATOR = " in ";
    public static final String BETWEEN_OPERATOR = " between ";
    public static final String ASC_ORDER = " asc";
    public static final String DESC_ORDER = " desc";
    public static final String WILD_CARD_OPERATOR = "%";
    public static final String WILD_CHAR_OPERATOR = "_";
    public static final String LEFT_PARENTHESIS = " ( ";
    public static final String RIGHT_PARENTHESIS = " ) ";
    public static final String COMMA = ",";
    public static final String ASTERISK = " * ";
    public static final String OR = " or ";
    public static final String AND = " and ";
    public static final String WHERE = " where ";
    public static final String QUESTION_MARK = " ? ";
    public static final String SPACE = " ";
    public static final String ORDER_BY = " order by ";
    public static final String GROUP_BY = " group by ";
    public static final String SELECT = " select ";
    public static final String SELECT_DISTINCT = " select distinct ";
    public static final String FROM = " from ";
    public static final String INNER_JOIN = " inner join ";
    public static final String LEFT_OUTER_JOIN = " left outer join ";
    public static final String CROSS_JOIN = " cross join ";
    public static final String NULL = " null ";
    public static final String IS_NULL = " is null ";
    public static final String IS_NOT_NULL = " is not null ";

    /** Sql SELECT clause. */
    private StringBuffer selectSql = null;

    /** Sql FROM clause. */
    private StringBuffer fromSql = null;

    /** Sql WHERE clause. */
    private StringBuffer whereSql = null;

    /** Sql ORDER BY clause. */
    private StringBuffer orderSql = null;

    /** Sql ORDER BY clause. */
    private StringBuffer groupSql = null;

    /** Sql WHERE clause replacement parameters. */
    private List paramValues = null;

    /** Name of the query for future reference. */
    private String queryName = null;

    /** Request size of batch to use when != 0.  0 for no change from the default, -1 for infinite. */
    private int batchSize = 0;

    /** Constructor. */
    public SqlQuery() {  }

    /** Clear all values as if freshly instantiated. */
    public void clear() {
        selectSql = null;
        fromSql = null;
        whereSql = null;
        orderSql = null;
        groupSql = null;
        paramValues = null;
    }

    private void continueFromClause() {
        if (fromSql == null) {
            throw new IllegalStateException(ERROR_SET_FROM);
        }
    }

    private void startOrContinueWhereClause() {
        if (whereSql == null) {
            whereSql = new StringBuffer(WHERE);
        } else {
            whereSql.append(AND);
        }
    }


    private void addParamValue(Object value) {
        if (paramValues == null) {
            paramValues = new ArrayList();
        }
        paramValues.add(value);
    }

    private void addParamValues(String sql, Object[] values) {
        int count = StringUtil.count(sql, '?');
        if (count > 0) {
            if (values == null || count != values.length) {
                throw new IllegalArgumentException("The number of values does not match the number of parameters ("
                        + count + ")!");
            }
        }
        for (int i = 0; i < count; i++) {
            Object value = values[i];
            if (value == null) {
                throw new IllegalArgumentException("Null values are not allowed!");
            }
            addParamValue(value);
        }
    }

    /**
     * Get parameter values added previously to this object.
     */
    public Object[] getParamValues() {
        Object[] paramValuesArray = null;
        if (paramValues != null) {
            paramValuesArray = paramValues.toArray(new Object[paramValues.size()]);
        }
        return paramValuesArray;
    }

    /** Get the name of the query. */
    public String getQueryName() {
        return queryName;
    }

    /** Set the name of the query. */
    public void setQueryName(String queryName) {
        this.queryName = queryName;
    }

    /** Get the name of the query. */
    public int getBatchSize() {
        return batchSize;
    }

    /** Set the name of the query. */
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    /** Get a query that counts records with count(*) rather than selecting the detail. */
    public String getCountQuery() {
        StringBuffer result = new StringBuffer();
        result.append(" select count(*) ");
        result.append(fromSql);
        result.append(whereSql).append(groupSql);
        return result.toString();
    }

    /** Get a query that selects the detail. */
    public String getSearchQuery() {
        StringBuffer result = new StringBuffer();
        result.append(selectSql != null ? selectSql : SELECT + ASTERISK);
        result.append(fromSql);
        result.append(whereSql).append(groupSql).append(orderSql);
        return result.toString();
    }

    public StringBuffer getSelect() {
        return selectSql;
    }

    public void setSelect(String sql) {
        if (sql != null) {
            selectSql = new StringBuffer(SELECT + sql);
        } else {
            selectSql = null;
        }
    }

    public void setSelect(String[] sqlColumns) {
        if (sqlColumns != null) {
            selectSql = new StringBuffer(SELECT);
            for (int i=0; i<sqlColumns.length; i++) {
                if (i != 0) selectSql.append(',');
                selectSql.append(sqlColumns[i]);
            }
        } else {
            selectSql = null;
        }
    }

    public void addSelect(String sql) {
        if (selectSql == null) {
            selectSql = new StringBuffer(SELECT + sql);
        } else {
            selectSql.append(',');
            selectSql.append(sql);
        }
    }

    public StringBuffer getFrom() {
        return fromSql;
    }

    public void setFrom(String sql) {
        if (sql != null) {
            fromSql = new StringBuffer(FROM + sql);
        } else {
            fromSql = null;
        }
    }

    public void addFromInnerJoin(String sql) {
        continueFromClause();
        addParamValues(sql, null);
        fromSql.append(INNER_JOIN).append(sql);
    }

    public void addFromInnerJoin(String sql, Object[] values) {
        continueFromClause();
        addParamValues(sql, values);
        fromSql.append(INNER_JOIN).append(sql);
    }

    public void addFromLeftOuterJoin(String sql) {
        continueFromClause();
        addParamValues(sql, null);
        fromSql.append(LEFT_OUTER_JOIN).append(sql);
    }

    public void addFromLeftOuterJoin(String sql, Object[] values) {
        continueFromClause();
        addParamValues(sql, values);
        fromSql.append(LEFT_OUTER_JOIN).append(sql);
    }

    public StringBuffer getWhere() {
        return whereSql;
    }

    public void setWhere(String sql) {
        if (sql != null) {
            whereSql = new StringBuffer(WHERE + sql);
        } else {
            whereSql = null;
        }
    }

    public void setWhere(String statement, Object[] values) {
        whereSql = null;
        addWhereCustom(statement, values);
    }

    public void setWhere(List names, List ops, List values) {
        if (names == null || values == null || ops == null ||
                !(names.size() == ops.size() && names.size() == values.size())) {
            throw new IllegalArgumentException("names, values, and ops must be supplied with the same sizes.");
        }
        whereSql = new StringBuffer();
        whereSql.append(WHERE);
        for (int i=0; i<names.size(); i++) {
            if (i>0) whereSql.append(AND);
            whereSql.append(names.get(i)).append(ops.get(i)).append(QUESTION_MARK);
        }
        paramValues = values;
    }

    public void addWhere(String name, String oper, Map values, boolean allowNull) {
        Object value = values.get(name);
        addWhere(name, oper, value, allowNull);
    }

    public void addWhereAll(String oper, Map values, boolean allowNull) {
        for (Iterator it=values.entrySet().iterator(); it.hasNext();) {
            Map.Entry item = (Map.Entry) it.next();
            addWhere( (String)item.getKey(), oper, item.getValue(), allowNull);
        }
    }

    public void addWhere(String name, String oper, Object value, boolean allowNull) {
        if (value != null || allowNull) {
            startOrContinueWhereClause();
            if (allowNull && value == null) {
// Use IS NULL or IS NOT NULL, using = NULL will never match in SQL.
                if (EQUAL_OPERATOR.equalsIgnoreCase(oper)) {
                    whereSql.append(name).append(IS_NULL);
                } else if (NOT_EQUAL_OPERATOR.equalsIgnoreCase(oper)) {
                    whereSql.append(name).append(IS_NOT_NULL);
                } else {
                    whereSql.append(name).append(oper).append(NULL);
                }
            } else {
                whereSql.append(name).append(oper).append(QUESTION_MARK);
                addParamValue(value);
            }
        }
    }

    public void addWhereNotNull(String name, String oper, Map values) {
        addWhere(name, oper, values, false);
    }

    public void addWhereNotNull(String name, String oper, Object value) {
        addWhere(name, oper, value, false);
    }

    public void addWhereNotEmpty(String name, String oper, String value) {
        if (value != null && !value.trim().isEmpty()) {
            addWhere(name, oper, value, false);
        }
    }

    public void addWhereIsNull(String name) {
        startOrContinueWhereClause();
// Use IS NULL, using = NULL will never match in SQL.
        whereSql.append(name).append(IS_NULL);
    }

    public void addWhereIsNotNull(String name) {
        startOrContinueWhereClause();
// Use IS NOT NULL, using != NULL will never match in SQL.
        whereSql.append(name).append(IS_NOT_NULL);
    }

    public void addWhereConstant(String name, String oper, String value) {
        if (value != null) {
            startOrContinueWhereClause();
            whereSql.append(name).append(oper).append('\'').append(
                    StringUtil.replaceAll(value,SQL_CHAR_SEARCH,SQL_CHAR_REPLACE)
            ).append('\'');
        }
    }

    public void addWhereBetween(String keyLeft, String keyRight, String field, Map values) {
        Object valueLeft = values.get(keyLeft);
        Object valueRight = values.get(keyRight);
        addWhereBetween(field, valueLeft, valueRight);
    }

    public void addWhereBetween(String name, Object valueLeft, Object valueRight) {
        startOrContinueWhereClause();
        if ((valueLeft != null && valueRight != null)) {
            whereSql.append(name).append(BETWEEN_OPERATOR).append(QUESTION_MARK).append(AND).append(QUESTION_MARK);
            addParamValue(valueLeft);
            addParamValue(valueRight);
        }
    }

    public void addWhereNestedOr(String[] names, String[] operators, Object[] values, boolean allowNull) {
        for (int i=0; i < values.length; i++) {
            if (i == 0) {
                if (whereSql == null) {
                    whereSql = new StringBuffer(WHERE);
                } else {
                    whereSql.append(AND);
                }
                whereSql.append(LEFT_PARENTHESIS);
            } else {
                whereSql.append(OR);
            }
            if (values[i] != null || allowNull) {
                whereSql.append(names[i]);
                if (values[i] == null) {
// Use IS NULL, using = NULL will never match in SQL.
                    whereSql.append(IS_NULL);
                } else {
                    whereSql.append(operators[i]);
                    whereSql.append(QUESTION_MARK);
                    addParamValue(values[i]);
                }
            }
            if (i == values.length - 1) {
                whereSql.append(RIGHT_PARENTHESIS);
            }
        }
    }

    public void addWhereEqual(String name, Object value) {
        addWhere(name, EQUAL_OPERATOR, value, false);
    }

    public void addWhereEqualNullable(String name, Map values) {
        for (Iterator it=values.entrySet().iterator(); it.hasNext();) {
            Map.Entry item = (Map.Entry) it.next();
            addWhere( (String)item.getKey(), EQUAL_OPERATOR, item.getValue(), true);
        }
    }

    public void addWhereCustom(String statement, Object[] values) {
        if (statement == null) {
            throw new IllegalArgumentException("String sql must not be null!");
        }
// add the where clause to the statement
        startOrContinueWhereClause();
        whereSql.append(statement);
// add the parameter values
        if (values != null) for (int i=0; i<values.length; i++) {
            Object value = values[i];
            if (value == null) {
                throw new IllegalArgumentException("Null values are not allowed!");
            }
            addParamValue(value);
        }
    }

    public void addWhereNotEmpty(String name, String value, boolean wildCardPrefix, boolean wildCardSuffix) {
        if (value != null && !value.trim().isEmpty()) {
            StringBuffer valueBuffer = new StringBuffer(value);
            if (wildCardPrefix) {
                valueBuffer.insert(0, WILD_CARD_OPERATOR);
            }
            if (wildCardSuffix) {
                valueBuffer.append(WILD_CARD_OPERATOR);
            }
            addWhere(name, LIKE_OPERATOR, valueBuffer.toString(), false);
        }
    }

    public void addWhereIn (String name, Object[] values) {
        if (values == null) return;

        startOrContinueWhereClause();
        whereSql.append(name);
        whereSql.append(IN_OPERATOR);
        whereSql.append(LEFT_PARENTHESIS);
        for (int i=0; i < values.length; i++) {
            if (i > 0) {
                whereSql.append(COMMA);
            }
            whereSql.append(QUESTION_MARK);
            addParamValue(values[i]);
        }
        whereSql.append(RIGHT_PARENTHESIS);
    }


    /**
     * Add a comparison of two column names.
     */
    public void addWhereEqualColumns(String colName1, String colName2) {
        startOrContinueWhereClause();
        whereSql.append(colName1);
        whereSql.append(EQUAL_OPERATOR);
        whereSql.append(colName2);
    }

    public StringBuffer getOrder() {
        return orderSql;
    }

    public void setOrder(String sql) {
        if (sql != null) {
            orderSql = new StringBuffer(sql);
        } else {
            orderSql = null;
        }
    }

    public void addOrder(String name, String oper) {
        if (orderSql == null) {
            orderSql = new StringBuffer(ORDER_BY);
        }
        else {
            orderSql.append(',');
        }
        orderSql.append(name).append(' ').append(oper);
    }

    public void addGroupBy(String name) {
        if (groupSql == null) {
            groupSql = new StringBuffer(GROUP_BY);
        }
        else {
            groupSql.append(',');
        }
        groupSql.append(name);
        this.addSelect(name);
    }

    public StringBuffer getGroupBy() {
        return groupSql;
    }

    public void setGroupBy(String sql) {
        if (sql != null) {
            groupSql = new StringBuffer(sql);
        } else {
            groupSql = null;
        }
    }


}
