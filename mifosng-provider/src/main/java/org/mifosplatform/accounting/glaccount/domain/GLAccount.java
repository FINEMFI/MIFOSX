/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.mifosplatform.accounting.glaccount.domain;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.mifosplatform.accounting.glaccount.api.GLAccountJsonInputParams;
import org.mifosplatform.accounting.glaccount.data.GLAccountData;
import org.mifosplatform.infrastructure.codes.domain.CodeValue;
import org.mifosplatform.infrastructure.core.api.JsonCommand;
import org.mifosplatform.infrastructure.core.data.EnumOptionData;
import org.mifosplatform.organisation.staff.domain.Staff;
import org.springframework.data.jpa.domain.AbstractPersistable;

import javax.persistence.*;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "acc_gl_account", uniqueConstraints = {@UniqueConstraint(columnNames = {"gl_code"}, name = "acc_gl_code")})
public class GLAccount extends AbstractPersistable<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private GLAccount parent;

    @Column(name = "hierarchy", nullable = true, length = 50)
    private String hierarchy;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private final List<GLAccount> children = new LinkedList<>();

    @Column(name = "name", nullable = false, length = 45)
    private String name;

    @Column(name = "currency_code", nullable = true, length = 3) // TODO: should not be nullable
    private String currencyCode;

    @Column(name = "gl_code", nullable = false, length = 100)
    private String glCode;

    @Column(name = "disabled", nullable = false)
    private boolean disabled = false;

    @Column(name = "manual_journal_entries_allowed", nullable = false)
    private boolean manualEntriesAllowed = true;

    @Column(name = "classification_enum", nullable = false)
    private Integer type;

    @Column(name = "account_usage", nullable = false)
    private Integer usage;

    @Column(name = "description", nullable = true, length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id")
    private CodeValue tagId;

    @Column(name = "affects_loan", nullable = false)
    private boolean affectsLoan;

    @ManyToMany(mappedBy = "glAccounts")
    private List<Staff> staffs;

    protected GLAccount() {
        //
    }

    private GLAccount(final GLAccount parent, final String name, final String currencyCode, final String glCode, final boolean disabled,
                      final boolean manualEntriesAllowed, final Integer type, final Integer usage, final String description, final CodeValue tagId, final boolean affectsLoan) {
        this.name = StringUtils.defaultIfEmpty(name, null);
        this.currencyCode = StringUtils.defaultIfEmpty(currencyCode, null); // TODO: should use default currency
        this.glCode = StringUtils.defaultIfEmpty(glCode, null);
        this.disabled = BooleanUtils.toBooleanDefaultIfNull(disabled, false);
        this.manualEntriesAllowed = BooleanUtils.toBooleanDefaultIfNull(manualEntriesAllowed, true);
        this.usage = usage;
        this.type = type;
        this.description = StringUtils.defaultIfEmpty(description, null);
        this.parent = parent;
        this.tagId = tagId;
        this.affectsLoan = affectsLoan;
    }

    public static GLAccount fromJson(final GLAccount parent, final JsonCommand command, final CodeValue glAccountTagType) {
        final String name = command.stringValueOfParameterNamed(GLAccountJsonInputParams.NAME.getValue());
        final String currencyCode = command.stringValueOfParameterNamed(GLAccountJsonInputParams.CURRENCY_CODE.getValue());
        final String glCode = command.stringValueOfParameterNamed(GLAccountJsonInputParams.GL_CODE.getValue());
        final boolean disabled = command.booleanPrimitiveValueOfParameterNamed(GLAccountJsonInputParams.DISABLED.getValue());
        final boolean manualEntriesAllowed = command.booleanPrimitiveValueOfParameterNamed(GLAccountJsonInputParams.MANUAL_ENTRIES_ALLOWED.getValue());
        final Integer usage = command.integerValueSansLocaleOfParameterNamed(GLAccountJsonInputParams.USAGE.getValue());
        final Integer type = command.integerValueSansLocaleOfParameterNamed(GLAccountJsonInputParams.TYPE.getValue());
        final String description = command.stringValueOfParameterNamed(GLAccountJsonInputParams.DESCRIPTION.getValue());
        final boolean affectsLoan = command.booleanPrimitiveValueOfParameterNamed(GLAccountJsonInputParams.AFFECTS_LOAN.getValue());
        return new GLAccount(parent, name, currencyCode, glCode, disabled, manualEntriesAllowed, type, usage, description, glAccountTagType, affectsLoan);
    }

    public Map<String, Object> update(final JsonCommand command) {
        final Map<String, Object> actualChanges = new LinkedHashMap<>(15);
        handlePropertyUpdate(command, actualChanges, GLAccountJsonInputParams.DESCRIPTION.getValue(), this.description);
        handlePropertyUpdate(command, actualChanges, GLAccountJsonInputParams.DISABLED.getValue(), this.disabled);
        handlePropertyUpdate(command, actualChanges, GLAccountJsonInputParams.CURRENCY_CODE.getValue(), this.currencyCode);
        handlePropertyUpdate(command, actualChanges, GLAccountJsonInputParams.GL_CODE.getValue(), this.glCode);
        handlePropertyUpdate(command, actualChanges, GLAccountJsonInputParams.MANUAL_ENTRIES_ALLOWED.getValue(), this.manualEntriesAllowed);
        handlePropertyUpdate(command, actualChanges, GLAccountJsonInputParams.NAME.getValue(), this.name);
        handlePropertyUpdate(command, actualChanges, GLAccountJsonInputParams.PARENT_ID.getValue(), 0L);
        handlePropertyUpdate(command, actualChanges, GLAccountJsonInputParams.TYPE.getValue(), this.type, true);
        handlePropertyUpdate(command, actualChanges, GLAccountJsonInputParams.USAGE.getValue(), this.usage, true);
        handlePropertyUpdate(command, actualChanges, GLAccountJsonInputParams.TAGID.getValue(), this.tagId == null ? 0L : this.tagId.getId());
        handlePropertyUpdate(command, actualChanges, GLAccountJsonInputParams.AFFECTS_LOAN.getValue(), this.affectsLoan);
        return actualChanges;
    }

    private void handlePropertyUpdate(final JsonCommand command, final Map<String, Object> actualChanges, final String paramName,
                                      final Integer propertyToBeUpdated, final boolean sansLocale) {
        boolean changeDetected = false;
        if (sansLocale) {
            changeDetected = command.isChangeInIntegerSansLocaleParameterNamed(paramName, propertyToBeUpdated);
        } else {
            changeDetected = command.isChangeInIntegerParameterNamed(paramName, propertyToBeUpdated);
        }
        if (changeDetected) {
            Integer newValue = null;
            if (sansLocale) {
                newValue = command.integerValueSansLocaleOfParameterNamed(paramName);
            } else {
                newValue = command.integerValueOfParameterNamed(paramName);
            }
            actualChanges.put(paramName, newValue);
            // now update actual property
            if (paramName.equals(GLAccountJsonInputParams.TYPE.getValue())) {
                this.type = newValue;
            } else if (paramName.equals(GLAccountJsonInputParams.USAGE.getValue())) {
                this.usage = newValue;
            }
        }
    }

    private void handlePropertyUpdate(final JsonCommand command, final Map<String, Object> actualChanges, final String paramName,
                                      final String propertyToBeUpdated) {
        if (command.isChangeInStringParameterNamed(paramName, propertyToBeUpdated)) {
            final String newValue = command.stringValueOfParameterNamed(paramName);
            actualChanges.put(paramName, newValue);
            // now update actual property
            if (paramName.equals(GLAccountJsonInputParams.DESCRIPTION.getValue())) {
                this.description = newValue;
            } else if (paramName.equals(GLAccountJsonInputParams.CURRENCY_CODE.getValue())) {
                this.currencyCode = newValue;
            } else if (paramName.equals(GLAccountJsonInputParams.GL_CODE.getValue())) {
                this.glCode = newValue;
            } else if (paramName.equals(GLAccountJsonInputParams.NAME.getValue())) {
                this.name = newValue;
            }
        }
    }

    private void handlePropertyUpdate(final JsonCommand command, final Map<String, Object> actualChanges, final String paramName,
                                      final Long propertyToBeUpdated) {
        if (command.isChangeInLongParameterNamed(paramName, propertyToBeUpdated)) {
            final Long newValue = command.longValueOfParameterNamed(paramName);
            actualChanges.put(paramName, newValue);
            // now update actual property
            if (paramName.equals(GLAccountJsonInputParams.PARENT_ID.getValue())) {
                // do nothing as this is a nested property
            }
        }
    }

    private void handlePropertyUpdate(final JsonCommand command, final Map<String, Object> actualChanges, final String paramName,
                                      final boolean propertyToBeUpdated) {
        if (command.isChangeInBooleanParameterNamed(paramName, propertyToBeUpdated)) {
            final Boolean newValue = command.booleanObjectValueOfParameterNamed(paramName);
            actualChanges.put(paramName, newValue);
            // now update actual property
            if (paramName.equals(GLAccountJsonInputParams.MANUAL_ENTRIES_ALLOWED.getValue())) {
                this.manualEntriesAllowed = newValue;
            } else if (paramName.equals(GLAccountJsonInputParams.DISABLED.getValue())) {
                this.disabled = newValue;
            } else if (paramName.equals(GLAccountJsonInputParams.AFFECTS_LOAN.getValue())) {
                this.affectsLoan = newValue;
            }
        }
    }

    public boolean isHeaderAccount() {
        return GLAccountUsage.HEADER.getValue().equals(this.usage);
    }

    public Integer getUsage() {
        return this.usage;
    }

    public List<GLAccount> getChildren() {
        return this.children;
    }

    public boolean isDisabled() {
        return this.disabled;
    }

    public boolean isManualEntriesAllowed() {
        return this.manualEntriesAllowed;
    }

    public String getCurrencyCode() {
        return this.currencyCode;
    }

    public String getGlCode() {
        return this.glCode;
    }

    public String getName() {
        return this.name;
    }

    public Integer getType() {
        return this.type;
    }

    public void generateHierarchy() {

        if (this.parent != null) {
            this.hierarchy = this.parent.hierarchyOf(getId());
        } else {
            this.hierarchy = ".";
        }
    }

    private String hierarchyOf(final Long id) {
        return this.hierarchy + id.toString() + ".";
    }

    public boolean isDetailAccount() {
        return GLAccountUsage.DETAIL.getValue().equals(this.usage);
    }

    public void updateTagId(final CodeValue tagID) {
        this.tagId = tagID;
    }

    public void updateParentAccount(final GLAccount parentAccount) {
        this.parent = parentAccount;
        generateHierarchy();
    }

    public GLAccountData toData() {
        Long parentId = null;
        if (this.parent != null) {
            parentId = this.parent.getId();
        }

        GLAccountType glAccountType = GLAccountType.fromInt(this.getType());
        EnumOptionData glAccountTypeData = new EnumOptionData(glAccountType.getValue().longValue(), glAccountType.getCode(), glAccountType.toString());

        GLAccountUsage glAccountUsage = GLAccountUsage.fromInt(this.getUsage());
        EnumOptionData glAccountUsageData = new EnumOptionData(glAccountUsage.getValue().longValue(), glAccountUsage.getCode(), glAccountUsage.toString());


        return new GLAccountData(this.getId(), this.name, parentId, this.currencyCode, this.glCode, this.disabled,
                this.manualEntriesAllowed, glAccountTypeData, glAccountUsageData, this.description,
                this.name, this.tagId.toData(), null, this.affectsLoan);
    }

    public boolean isAffectsLoan() {
        return affectsLoan;
    }

    public void setAffectsLoan(boolean affectsLoan) {
        this.affectsLoan = affectsLoan;
    }

    public List<Staff> getStaffs() {
        return staffs;
    }

    public void setStaffs(List<Staff> staffs) {
        this.staffs = staffs;
    }
}