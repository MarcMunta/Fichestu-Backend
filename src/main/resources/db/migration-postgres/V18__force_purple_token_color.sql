UPDATE tokens
SET name = 'Ficha Morada',
    color_code = '#8B5CF6'
WHERE token_id = 4
   OR LOWER(name) IN ('ficha dorada', 'ficha morada');
