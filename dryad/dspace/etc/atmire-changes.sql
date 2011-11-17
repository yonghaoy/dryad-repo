CREATE TABLE versionhistory
(
  versionhistory_id integer NOT NULL PRIMARY KEY
);

CREATE TABLE versionitem
(
  versionitem_id integer NOT NULL PRIMARY KEY,
  item_id_fkey INTEGER REFERENCES Item(item_id),
  version_number integer,
  eperson_id_fkey INTEGER REFERENCES EPerson(eperson_id),
  version_date TIMESTAMP,
  version_summary VARCHAR(255),
  versionhistory_id_fkey INTEGER REFERENCES VersionHistory(versionhistory_id)
);

CREATE SEQUENCE versionitem_seq;
CREATE SEQUENCE versionhistory_seq;