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
  id bigint not null auto_increment primary key, course_id bigint not null, owner_id bigint, kind varchar(30) not null, name varchar(255) not null, source_label varchar(120) not null, shared boolean not null default false, content_type varchar(120), storage_backend varchar(40), storage_key varchar(500), file_size bigint, content longblob, created_at timestamp not null,
  constraint fk_mysql_resource_course foreign key (course_id) references course(id) on delete cascade,
  constraint fk_mysql_resource_owner foreign key (owner_id) references user_account(id) on delete set null
);
create table if not exists learning_task (
  id bigint not null auto_increment primary key, course_id bigint not null, task_type varchar(20) not null, name varchar(180) not null, description text, start_at timestamp not null, deadline timestamp not null, max_score int not null default 100, questions_json text, created_at timestamp not null,
  constraint fk_mysql_task_course foreign key (course_id) references course(id) on delete cascade
);
create table if not exists task_submission (
  id bigint not null auto_increment primary key, task_id bigint not null, student_name varchar(80) not null, student_no varchar(40) not null, submitted boolean not null default false, submitted_at timestamp null, ai_score int, teacher_score int, answers_json text, report_text text, ai_review text, teacher_comment text, review_status varchar(20) not null default 'SUBMITTED', current_version_no int not null default 0,
  constraint fk_mysql_submission_task foreign key (task_id) references learning_task(id) on delete cascade
);
alter table course_resource add column if not exists storage_backend varchar(40);
alter table course_resource add column if not exists storage_key varchar(500);
alter table course_resource add column if not exists file_size bigint;
alter table task_submission add column if not exists review_status varchar(20) not null default 'SUBMITTED';
alter table task_submission add column if not exists current_version_no int not null default 0;
create table if not exists submission_version (
  id bigint not null auto_increment primary key, submission_id bigint not null, version_no int not null, report_text text, attachment_json text, ai_score int, ai_review text, created_at timestamp not null,
  unique key uq_mysql_submission_version (submission_id, version_no),
  constraint fk_mysql_version_submission foreign key (submission_id) references task_submission(id) on delete cascade
);
create table if not exists course_enrollment (
  id bigint not null auto_increment primary key, course_id bigint not null, student_id bigint not null, active boolean not null default true, joined_at timestamp not null,
  unique key uq_mysql_course_student (course_id, student_id),
  constraint fk_mysql_enrollment_course foreign key (course_id) references course(id) on delete cascade,
  constraint fk_mysql_enrollment_student foreign key (student_id) references user_account(id) on delete cascade
);
create table if not exists course_invite_code (
  id bigint not null auto_increment primary key, course_id bigint not null, invite_code varchar(20) not null unique, enabled boolean not null default true, expires_at timestamp null, created_at timestamp not null,
  constraint fk_mysql_invite_course foreign key (course_id) references course(id) on delete cascade
);
create table if not exists user_notification (
  id bigint not null auto_increment primary key, user_id bigint not null, notification_type varchar(30) not null, title varchar(220) not null, content varchar(1000) not null, source_type varchar(40), source_id bigint, is_read boolean not null default false, created_at timestamp not null,
  constraint fk_mysql_notification_user foreign key (user_id) references user_account(id) on delete cascade
);
create table if not exists rubric_template (
  id bigint not null auto_increment primary key, teacher_id bigint not null, name varchar(160) not null, dimensions_json text not null, enabled boolean not null default true, version_no int not null default 1, created_at timestamp not null, updated_at timestamp not null,
  constraint fk_mysql_rubric_teacher foreign key (teacher_id) references user_account(id) on delete cascade
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
  id bigint not null auto_increment primary key, teacher_id bigint not null, student_id bigint, contact_name varchar(120) not null, contact_type varchar(20) not null, avatar_text varchar(10), unread_count int not null default 0, updated_at timestamp not null,
  constraint fk_mysql_conversation_teacher foreign key (teacher_id) references user_account(id) on delete cascade,
  constraint fk_mysql_conversation_student foreign key (student_id) references user_account(id) on delete set null
);
alter table conversation add column if not exists student_id bigint;
create table if not exists conversation_message (
  id bigint not null auto_increment primary key, conversation_id bigint not null, sender varchar(20) not null, title varchar(220), content text not null, created_at timestamp not null,
  constraint fk_mysql_conversation_message_conversation foreign key (conversation_id) references conversation(id) on delete cascade
);
