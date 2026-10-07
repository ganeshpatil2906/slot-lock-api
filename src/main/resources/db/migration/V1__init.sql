CREATE TABLE users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  email VARCHAR(150) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  role VARCHAR(20) NOT NULL
);

CREATE TABLE resources (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  type VARCHAR(50) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE slots (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  resource_id BIGINT NOT NULL,
  start_time DATETIME NOT NULL,
  end_time DATETIME NOT NULL,
  status VARCHAR(20) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_slot_resource FOREIGN KEY (resource_id) REFERENCES resources(id),
  CONSTRAINT uq_slot UNIQUE (resource_id, start_time)
);

CREATE TABLE bookings (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  slot_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL,
  idempotency_key VARCHAR(64) NOT NULL UNIQUE,
  hold_expires_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT fk_booking_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_booking_slot FOREIGN KEY (slot_id) REFERENCES slots(id)
);

CREATE INDEX idx_slots_status ON slots(status);
CREATE INDEX idx_bookings_hold ON bookings(status, hold_expires_at);
