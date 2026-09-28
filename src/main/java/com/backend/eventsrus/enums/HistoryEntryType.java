package com.backend.eventsrus.enums;

/**
 * Discriminates the two kinds of row a quotation's history timeline can
 * contain - see QuotationService#history, which merges real state-machine
 * transitions (QuotationStatusEvent rows) with free-standing attachments
 * (QuotationAttachment rows, which never touch Quotation#status at all)
 * into one chronologically-sorted list. Every template/consumer of that
 * list must check this before reading fromStatus/toStatus, which are only
 * ever populated for STATUS_CHANGE rows.
 */
public enum HistoryEntryType {
    STATUS_CHANGE,
    ATTACHMENT
}
