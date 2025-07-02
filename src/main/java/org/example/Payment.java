package org.example;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class Payment {
    private String registryStringUID;
    private String paymentType;
    private long paymentSum;
    private String paymentPurpose;
    private String paymentOrder;
    private String operationKind;
    private String receiverBankBic;
    private String receiverCorrAccount;
    private String receiverINN;
    private String receiverAccountNumber;
    private String receiverName;

    public String toXml() {
        return String.format(
                "<Payment RegistryStringUID=\"%s\" PaymentType=\"%s\" PaymentSum=\"%d\" PaymentPurpose=\"%s\" PaymentOrder=\"%s\" OperationKind=\"%s\" ReceiverBankBic=\"%s\" ReceiverCorrAccount=\"%s\" ReceiverINN=\"%s\" ReceiverAccountNumber=\"%s\" ReceiverName=\"%s\"/>",
                registryStringUID, paymentType, paymentSum, paymentPurpose, paymentOrder, operationKind, receiverBankBic, receiverCorrAccount, receiverINN, receiverAccountNumber, receiverName
        );
    }
}
