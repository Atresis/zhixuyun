create table if not exists user_account (
  id bigint not null auto_increment primary key,
  login_name varchar(120) not null unique,
  password_hash varchar(100) not null,
  display_name varchar(80) not null,
  role varchar(20) not null,
  enabled boolean not null default true,
  email varchar(120), phone varchar(40), bio varchar(200),
  must_change_password boolean not null default false,
  failed_login_attempts int not null default 0,
  avatar_content_type varchar(120), avatar_content longblob
);
create table if not exists auth_session (
  token varchar(100) primary key,
  user_id bigint not null,
  expires_at timestamp not null,
  constraint fk_mysql_session_user foreign key (user_id) references user_account(id) on delete cascade
);
create table if not exists teacher_profile (
  user_id bigint primary key,
  department varchar(100) not null, title varchar(60) not null,
  email varchar(120), phone varchar(40), bio varchar(1000),
  constraint fk_mysql_teacher_profile_user foreign key (user_id) references user_account(id) on delete cascade
);
create table if not exists administrative_class (
  id bigint not null auto_increment primary key,
  name varchar(120) not null, grade_year varchar(20) not null, major_name varchar(120), enabled boolean not null default true
);
create table if not exists student_profile (
  user_id bigint primary key, student_no varchar(40) not null unique, grade_year varchar(20), administrative_class_id bigint,
  constraint fk_mysql_student_profile_user foreign key (user_id) references user_account(id) on delete cascade,
  constraint fk_mysql_student_profile_class foreign key (administrative_class_id) references administrative_class(id) on delete set null
);
create table if not exists course (
  id bigint not null auto_increment primary key,
  teacher_id bigint not null, name varchar(120) not null, code varchar(40) not null, class_name varchar(120) not null,
  semester varchar(80) not null, schedule_text varchar(200), student_count int not null default 0, color varchar(20) not null default '#07876e',
  constraint fk_mysql_course_teacher foreign key (teacher_id) references user_account(id) on delete cascade
);
create table if not exists teaching_class (
  id bigint not null auto_increment primary key, course_id bigint not null, name varchar(120) not null, term varchar(80) not null, enabled boolean not null default true,
  constraint fk_mysql_teaching_class_course foreign key (course_id) references course(id) on delete cascade
);
create table if not exists course_teacher_assignment (
  id bigint not null auto_increment primary key, course_id bigint not null, teacher_id bigint not null, role_code varchar(30) not null default 'INSTRUCTOR', subject_or_duty varchar(160),
  constraint fk_mysql_cta_course foreign key (course_id) references course(id) on delete cascade,
  constraint fk_mysql_cta_teacher foreign key (teacher_id) references user_account(id) on delete cascade
);
create table if not exists teaching_class_teacher_assignment (
  id bigint not null auto_increment primary key, teaching_class_id bigint not null, teacher_id bigint not null, role_code varchar(30) not null default 'INSTRUCTOR', subject_or_duty varchar(160),
  constraint fk_mysql_tcta_class foreign key (teaching_class_id) references teaching_class(id) on delete cascade,
  constraint fk_mysql_tcta_teacher foreign key (teacher_id) references user_account(id) on delete cascade
);
create table if not exists platform_setting (setting_key varchar(80) primary key, setting_value varchar(500));
create table if not exists audit_log (
  id bigint not null auto_increment primary key, actor_id bigint, action varchar(80) not null, target_type varchar(80), target_id varchar(80), detail varchar(500), created_at timestamp not null,
  constraint fk_mysql_audit_actor foreign key (actor_id) references user_account(id) on delete set null
);
create table if not exists course_resource (
  id bigint not null auto_increment primary key, course_id bigint not null, owner_id bigint, kind varchar(30) not null, name varchar(255) not null, source_label varchar(120) not null, shared boolean not null default false, content_type varchar(120), content longblob, created_at timestamp not null,
  constraint fk_mysql_resource_course foreign key (course_id) references course(id) on delete cascade,
  constraint fk_mysql_resource_owner foreign key (owner_id) references user_account(id) on delete set null
);
create table if not exists learning_task (
  id bigint not null auto_increment primary key, course_id bigint not null, task_type varchar(20) not null, name varchar(180) not null, description text, start_at timestamp not null, deadline timestamp not null, max_score int not null default 100, questions_json text, created_at timestamp not null,
  constraint fk_mysql_task_course foreign key (course_id) references course(id) on delete cascade
);
create table if not exists task_submission (
  id bigint not null auto_increment primary key, task_id bigint not null, student_name varchar(80) not null, student_no varchar(40) not null, submitted boolean not null default false, submitted_at timestamp null, ai_score int, teacher_score int, answers_json text, report_text text, ai_review text, teacher_comment text,
  constraint fk_mysql_submission_task foreign key (task_id) references learning_task(id) on delete cascade
);
create table if not exists teaching_alert (
  id bigint not null auto_increment primary key, teacher_id bigint not null, title varchar(240) not null, summary varchar(500) not null, target_name varchar(160) not null, level varchar(20) not null, status varchar(20) not null, analysis text not null, evidence text, proposal text, created_at timestamp not null,
  constraint fk_mysql_alert_teacher foreign key (teacher_id) references user_account(id) on delete cascade
);
create table if not exists assistant_session (
  id bigint not null auto_increment primary key, teacher_id bigint not null, title varchar(200) not null, created_at timestamp not null, updated_at timestamp not null,
  constraint fk_mysql_assistant_session_teacher foreign key (teacher_id) references user_account(id) on delete cascade
);
create table if not exists assistant_message (
  id bigint not null auto_increment primary key, session_id bigint not null, role varchar(20) not null, content text not null, created_at timestamp not null,
  constraint fk_mysql_assistant_message_session foreign key (session_id) references assistant_session(id) on delete cascade
);
create table if not exists conversation (
  id bigint not null auto_increment primary key, teacher_id bigint not null, contact_name varchar(120) not null, contact_type varchar(20) not null, avatar_text varchar(10), unread_count int not null default 0, updated_at timestamp not null,
  constraint fk_mysql_conversation_teacher foreign key (teacher_id) references user_account(id) on delete cascade
);
create table if not exists conversation_message (
  id bigint not null auto_increment primary key, conversation_id bigint not null, sender varchar(20) not null, title varchar(220), content text not null, created_at timestamp not null,
  constraint fk_mysql_conversation_message_conversation foreign key (conversation_id) references conversation(id) on delete cascade
);
