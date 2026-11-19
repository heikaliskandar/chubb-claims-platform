CREATE TABLE employee (
    username          VARCHAR(50)  PRIMARY KEY,
    employee_number   VARCHAR(20)  NOT NULL UNIQUE,
    full_name         VARCHAR(100) NOT NULL,
    email             VARCHAR(150) NOT NULL UNIQUE,
    phone             VARCHAR(30),
    position          VARCHAR(30)  NOT NULL,
    department        VARCHAR(50)  NOT NULL,
    team              VARCHAR(50)  NOT NULL,
    market            VARCHAR(2)   NOT NULL,
    office_location   VARCHAR(80),
    manager_username  VARCHAR(50)  REFERENCES employee (username),
    join_date         DATE         NOT NULL,
    employment_type   VARCHAR(20)  NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    CONSTRAINT ck_employee_not_own_manager CHECK (manager_username <> username)
);

CREATE INDEX ix_employee_manager ON employee (manager_username);
