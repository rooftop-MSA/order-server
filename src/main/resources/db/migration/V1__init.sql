create table if not exists order(
  id bigint primary key,
  product_id bigint not null,
  product_quantity bigint not null check(product_quantity > 0),
  total_price bigint not null check(total_price > 0),
  state varchar(10) not null check(state in ('PENDING', 'SUCCESS', 'FAIL')),
  version int not null
);
