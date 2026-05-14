alter table game_sessions
    add column if not exists battle_round_deadline timestamp null;

alter table match_cards
    add column if not exists round_number int null,
    add column if not exists target_user_id int null;

create index if not exists idx_match_cards_match_round
    on match_cards (match_id, round_number);
