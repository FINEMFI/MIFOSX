/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.mifosplatform.portfolio.collectionsheet.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.mifosplatform.infrastructure.core.api.JsonCommand;
import org.mifosplatform.infrastructure.core.data.CommandProcessingResult;
import org.mifosplatform.infrastructure.core.data.CommandProcessingResultBuilder;
import org.mifosplatform.portfolio.collectionsheet.command.CollectionSheetBulkDisbursalCommand;
import org.mifosplatform.portfolio.collectionsheet.command.CollectionSheetBulkRepaymentCommand;
import org.mifosplatform.portfolio.collectionsheet.data.CollectionSheetTransactionDataValidator;
import org.mifosplatform.portfolio.collectionsheet.serialization.CollectionSheetBulkDisbursalCommandFromApiJsonDeserializer;
import org.mifosplatform.portfolio.collectionsheet.serialization.CollectionSheetBulkRepaymentCommandFromApiJsonDeserializer;
import org.mifosplatform.portfolio.loanaccount.service.LoanWritePlatformService;
import org.mifosplatform.portfolio.meeting.service.MeetingWritePlatformService;
import org.mifosplatform.portfolio.savings.data.SavingsAccountTransactionDTO;
import org.mifosplatform.portfolio.savings.domain.DepositAccountAssembler;
import org.mifosplatform.portfolio.savings.service.DepositAccountWritePlatformService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CollectionSheetWritePlatformServiceJpaRepositoryImpl implements CollectionSheetWritePlatformService {

    private final LoanWritePlatformService loanWritePlatformService;
    private final CollectionSheetBulkRepaymentCommandFromApiJsonDeserializer bulkRepaymentCommandFromApiJsonDeserializer;
    private final CollectionSheetBulkDisbursalCommandFromApiJsonDeserializer bulkDisbursalCommandFromApiJsonDeserializer;
    private final CollectionSheetTransactionDataValidator transactionDataValidator;
    private final MeetingWritePlatformService meetingWritePlatformService;
    private final DepositAccountAssembler accountAssembler;
    private final DepositAccountWritePlatformService accountWritePlatformService;

    @Autowired
    public CollectionSheetWritePlatformServiceJpaRepositoryImpl(final LoanWritePlatformService loanWritePlatformService,
            final CollectionSheetBulkRepaymentCommandFromApiJsonDeserializer bulkRepaymentCommandFromApiJsonDeserializer,
            final CollectionSheetBulkDisbursalCommandFromApiJsonDeserializer bulkDisbursalCommandFromApiJsonDeserializer,
            final CollectionSheetTransactionDataValidator transactionDataValidator,
            final MeetingWritePlatformService meetingWritePlatformService, final DepositAccountAssembler accountAssembler,
            final DepositAccountWritePlatformService accountWritePlatformService) {
        this.loanWritePlatformService = loanWritePlatformService;
        this.bulkRepaymentCommandFromApiJsonDeserializer = bulkRepaymentCommandFromApiJsonDeserializer;
        this.bulkDisbursalCommandFromApiJsonDeserializer = bulkDisbursalCommandFromApiJsonDeserializer;
        this.transactionDataValidator = transactionDataValidator;
        this.meetingWritePlatformService = meetingWritePlatformService;
        this.accountAssembler = accountAssembler;
        this.accountWritePlatformService = accountWritePlatformService;
    }

    @Override
    public CommandProcessingResult updateCollectionSheet(final JsonCommand command) {

        this.transactionDataValidator.validateTransaction(command);

        final Map<String, Object> changes = new HashMap<>();
        changes.put("locale", command.locale());
        changes.put("dateFormat", command.dateFormat());

        final String noteText = command.stringValueOfParameterNamed("note");
        if (StringUtils.isNotBlank(noteText)) {
            changes.put("note", noteText);
        }

        changes.putAll(updateBulkReapayments(command));

        changes.putAll(updateBulkDisbursals(command));
        
        changes.putAll(updateBulkMandatorySavingsDuePayments(command));

        this.meetingWritePlatformService.updateCollectionSheetAttendance(command);

        return new CommandProcessingResultBuilder() //
                .withCommandId(command.commandId()) //
                .withEntityId(command.entityId()) //
                .withGroupId(command.entityId()) //
                .with(changes).with(changes).build();
    }

    private Map<String, Object> updateBulkReapayments(final JsonCommand command) {
        final Map<String, Object> changes = new HashMap<>();
        final CollectionSheetBulkRepaymentCommand bulkRepaymentCommand = this.bulkRepaymentCommandFromApiJsonDeserializer
                .commandFromApiJson(command.json());
        changes.putAll(this.loanWritePlatformService.makeLoanBulkRepayment(bulkRepaymentCommand));
        return changes;
    }

    private Map<String, Object> updateBulkDisbursals(final JsonCommand command) {
        final Map<String, Object> changes = new HashMap<>();
        final CollectionSheetBulkDisbursalCommand bulkDisbursalCommand = this.bulkDisbursalCommandFromApiJsonDeserializer
                .commandFromApiJson(command.json());
        changes.putAll(this.loanWritePlatformService.bulkLoanDisbursal(command, bulkDisbursalCommand, false));
        return changes;
    }

    private Map<String, Object> updateBulkMandatorySavingsDuePayments(final JsonCommand command) {
        final Map<String, Object> changes = new HashMap<>();
        final Collection<SavingsAccountTransactionDTO> savingsTransactions = this.accountAssembler.assembleBulkMandatorySavingsAccountTransactionDTOs(command);
        
        for (SavingsAccountTransactionDTO savingsAccountTransactionDTO : savingsTransactions) {
            try {
                this.accountWritePlatformService.mandatorySavingsAccountDeposit(savingsAccountTransactionDTO);
                changes.put("savingsAccountId", savingsAccountTransactionDTO.getSavingsAccountId());
                changes.put("transationAmount", savingsAccountTransactionDTO.getTransactionAmount());
            } catch (Exception e) {
                // TODO: handle exception
            }
        }
        
        return changes;
    }
}
