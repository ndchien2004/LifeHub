package com.lifehub.application.calendar;

/**
 * The payload pushed to the Electron shell when a reminder fires (06-API-SPEC.md §6).
 *
 * <p>Carries exactly what a native notification needs and nothing more: a title, a one line body,
 * and the reference the shell navigates to when the user clicks it. No event body, description or
 * amount travels this way - the notification is a pointer, not a copy of the record.
 *
 * @param reminderId identifies the reminder for the snooze and dismiss buttons
 * @param refType {@code EVENT} or {@code TASK}
 * @param refId the event or task the notification points at
 */
public record ReminderNotification(
        String reminderId, String title, String body, String refType, String refId) {

    public static final String EVENT_TYPE = "reminder.fired";
}
