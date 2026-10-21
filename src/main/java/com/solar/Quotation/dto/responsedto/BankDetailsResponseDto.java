package com.solar.Quotation.dto.responsedto;

public class BankDetailsResponseDto {

    private String accountName;
    private String bankName;
    private String accountNumber;
    private String ifsc;
    private String branch;

    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getIfsc() { return ifsc; }
    public void setIfsc(String ifsc) { this.ifsc = ifsc; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
}
