create table if not exists orders(
  id bigint primary key,
  user_id bigint not null,
  product_id bigint not null,
  product_quantity bigint not null check(product_quantity > 0),
  total_price bigint not null check(total_price > 0),
  state varchar(10) not null check(state in ('PENDING', 'SUCCESS', 'FAILED')),
  created_at TIMESTAMP(6) not null,
  modified_at TIMESTAMP(6) not null,
  version int not null
);
