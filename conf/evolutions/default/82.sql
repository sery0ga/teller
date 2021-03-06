# --- !Ups
create table BRAND_FEE(
  ID BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  BRAND CHAR(5) NOT NULL,
  COUNTRY CHAR(2) NOT NULL,
  FEE_CURRENCY CHAR(3) NOT NULL,
  FEE_AMOUNT DECIMAL(13, 3) NOT NULL,
  UNIQUE KEY BRAND_COUNTRY (BRAND, COUNTRY)
) collate 'utf8_unicode_ci';

# --- !Downs
drop table EVENT_FEE;