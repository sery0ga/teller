# T102 Delete booking entry

# --- !Ups

-- Make reference date optional in the database to allow inserting without it and then updating from a BookingEntryOverflow.
alter table BOOKING_ENTRY modify column REFERENCE_DATE date;

# --- !Downs

alter table BOOKING_ENTRY modify column REFERENCE_DATE date not null;
