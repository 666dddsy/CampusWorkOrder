

create database if not exists campus_work_order;
use campus_work_order;

select database();


create table if not exists app_user(
    id bigint unsigned not null auto_increment,
    username varchar(50) not null,
    password_hash varchar(255) not null,
    role varchar(50) not null,
    created_at DATETIME not null default current_timestamp,

    primary key (id),
    constraint uk_app_user_username unique (username),
    constraint chk_app_user_role check ( role in ('REQUESTER','WORKER','ADMIN') )
)engine=InnoDB;

DESC app_user;

create table if not exists ticket(
    id bigint unsigned not null auto_increment,
    creator_id bigint unsigned not null ,
    assignee_id bigint unsigned ,
    title varchar(100) not null ,
    description text not null ,
    location varchar(100) not null ,
    category varchar(30) not null ,
    priority varchar(30) not null default 'MEDIUM',
    status varchar(20) not null default 'CREATED',
    created_at datetime not null default current_timestamp ,
    updated_at datetime not null default current_timestamp on update current_timestamp,

    primary key (id),
    foreign key (creator_id) references app_user(id) on delete restrict ,
    foreign key (assignee_id) references app_user(id) on DELETE set null,
    check ( priority in('LOW','MEDIUM','HIGH','URGENT') ),
    CHECK (status IN ('CREATED','ASSIGNED','PROCESSING','RESOLVED','CLOSED')),
    CHECK ( category IN('PLUMBING','ELECTRICAL','NETWORK','EQUIPMENT','OTHER') ),

    index idx_ticket_status_created_at(status,created_at),
    index idx_ticket_assignee_status(assignee_id,status),
    index idx_ticket_creator_id(creator_id),
    index idx_ticket_created_at_id(created_at DESC,id DESC)
);
DESC ticket;

create table if not exists ticket_log(
    id bigint unsigned not null auto_increment,
    ticket_id bigint unsigned not null ,
    operator_id bigint unsigned not null ,
    from_status varchar(20) ,
    to_status varchar(20) not null ,
    note varchar(500),
    created_at datetime not null default current_timestamp,

    primary key (id),
    foreign key (ticket_id) references ticket(id),
    foreign key (operator_id) references app_user(id),

    check ( from_status in('CREATED','ASSIGNED','PROCESSING','RESOLVED','CLOSED') ),
    check ( to_status in('CREATED','ASSIGNED','PROCESSING','RESOLVED','CLOSED') ),

    index idx_ticket_log_ticket_time(ticket_id,created_at)
);

DESC ticket_log;

explain select * from ticket ;

