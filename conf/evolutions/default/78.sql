# --- !Ups
CREATE TABLE IF NOT EXISTS MEMBER (
  ID BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  OBJECT_ID BIGINT NOT NULL,
  PERSON TINYINT(1) NOT NULL,
  FUNDER TINYINT(1) NOT NULL,
  FEE DECIMAL(13,3) NOT NULL,
  TOTAL_FEE DECIMAL(13,3) NOT NULL,
  SINCE DATE NOT NULL);
# --- !Downs
DROP TABLE IF EXISTS MEMBER;
