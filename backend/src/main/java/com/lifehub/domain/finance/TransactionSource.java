package com.lifehub.domain.finance;

/** How a transaction entered the system (03-DATA-MODEL.md 2.8). */
public enum TransactionSource {
    MANUAL,
    AI_PARSE,
    CSV_IMPORT,
    RECURRING
}
