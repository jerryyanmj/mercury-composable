/*
 * This file is generated by jOOQ.
 */
package home.jerry.test.jooq;


import home.jerry.test.jooq.tables.Service;
import home.jerry.test.jooq.tables.Servicetokendata;
import home.jerry.test.jooq.tables.Tenant;
import home.jerry.test.jooq.tables.Tokendata;
import home.jerry.test.jooq.tables.Tokentype;
import home.jerry.test.jooq.tables.records.ServiceRecord;
import home.jerry.test.jooq.tables.records.ServicetokendataRecord;
import home.jerry.test.jooq.tables.records.TenantRecord;
import home.jerry.test.jooq.tables.records.TokendataRecord;
import home.jerry.test.jooq.tables.records.TokentypeRecord;

import org.jooq.ForeignKey;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.Internal;


/**
 * A class modelling foreign key relationships and constraints of tables in
 * Token.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class Keys {

    // -------------------------------------------------------------------------
    // UNIQUE and PRIMARY KEY definitions
    // -------------------------------------------------------------------------

    public static final UniqueKey<ServiceRecord> KEY_SERVICE_IX_SERVICE_NAME = Internal.createUniqueKey(Service.SERVICE, DSL.name("KEY_Service_IX_Service_Name"), new TableField[] { Service.SERVICE.NAME }, true);
    public static final UniqueKey<ServiceRecord> KEY_SERVICE_PRIMARY = Internal.createUniqueKey(Service.SERVICE, DSL.name("KEY_Service_PRIMARY"), new TableField[] { Service.SERVICE.ID }, true);
    public static final UniqueKey<ServicetokendataRecord> KEY_SERVICETOKENDATA_PRIMARY = Internal.createUniqueKey(Servicetokendata.SERVICETOKENDATA, DSL.name("KEY_ServiceTokenData_PRIMARY"), new TableField[] { Servicetokendata.SERVICETOKENDATA.ID }, true);
    public static final UniqueKey<TenantRecord> KEY_TENANT_IX_TENANT_NAME = Internal.createUniqueKey(Tenant.TENANT, DSL.name("KEY_Tenant_IX_Tenant_Name"), new TableField[] { Tenant.TENANT.NAME }, true);
    public static final UniqueKey<TenantRecord> KEY_TENANT_PRIMARY = Internal.createUniqueKey(Tenant.TENANT, DSL.name("KEY_Tenant_PRIMARY"), new TableField[] { Tenant.TENANT.ID }, true);
    public static final UniqueKey<TokendataRecord> KEY_TOKENDATA_PRIMARY = Internal.createUniqueKey(Tokendata.TOKENDATA, DSL.name("KEY_TokenData_PRIMARY"), new TableField[] { Tokendata.TOKENDATA.ID }, true);
    public static final UniqueKey<TokentypeRecord> KEY_TOKENTYPE_IX_TOKENTYPE_TYPE = Internal.createUniqueKey(Tokentype.TOKENTYPE, DSL.name("KEY_TokenType_IX_TokenType_Type"), new TableField[] { Tokentype.TOKENTYPE.TYPE }, true);
    public static final UniqueKey<TokentypeRecord> KEY_TOKENTYPE_PRIMARY = Internal.createUniqueKey(Tokentype.TOKENTYPE, DSL.name("KEY_TokenType_PRIMARY"), new TableField[] { Tokentype.TOKENTYPE.ID }, true);

    // -------------------------------------------------------------------------
    // FOREIGN KEY definitions
    // -------------------------------------------------------------------------

    public static final ForeignKey<ServicetokendataRecord, ServiceRecord> FK_SERVICETOKENDATA_SERVICE_SERVICEID = Internal.createForeignKey(Servicetokendata.SERVICETOKENDATA, DSL.name("FK_ServiceTokenData_Service_ServiceId"), new TableField[] { Servicetokendata.SERVICETOKENDATA.SERVICEID }, Keys.KEY_SERVICE_PRIMARY, new TableField[] { Service.SERVICE.ID }, true);
    public static final ForeignKey<ServicetokendataRecord, TokendataRecord> FK_SERVICETOKENDATA_TOKENDATA_TOKENDATAID = Internal.createForeignKey(Servicetokendata.SERVICETOKENDATA, DSL.name("FK_ServiceTokenData_TokenData_TokenDataId"), new TableField[] { Servicetokendata.SERVICETOKENDATA.TOKENDATAID }, Keys.KEY_TOKENDATA_PRIMARY, new TableField[] { Tokendata.TOKENDATA.ID }, true);
    public static final ForeignKey<TokendataRecord, TenantRecord> FK_TOKENDATA_TENANT_TENANTID = Internal.createForeignKey(Tokendata.TOKENDATA, DSL.name("FK_TokenData_Tenant_TenantId"), new TableField[] { Tokendata.TOKENDATA.TENANTID }, Keys.KEY_TENANT_PRIMARY, new TableField[] { Tenant.TENANT.ID }, true);
    public static final ForeignKey<TokendataRecord, TokentypeRecord> FK_TOKENDATA_TOKENTYPE_TOKENTYPEID = Internal.createForeignKey(Tokendata.TOKENDATA, DSL.name("FK_TokenData_TokenType_TokenTypeId"), new TableField[] { Tokendata.TOKENDATA.TOKENTYPEID }, Keys.KEY_TOKENTYPE_PRIMARY, new TableField[] { Tokentype.TOKENTYPE.ID }, true);
}
