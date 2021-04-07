function expireRecord(rec)
        local currTTL = record.ttl(rec)
        if ( currTTL == 0 ) then
                record.set_ttl(rec, 86400)
                local result = aerospike:update(rec)
                if ( result ~= nil and result ~= 0 ) then
                        warn("expireRecord:Failed to UPDATE the record: resultCode (%s)", tostring(result))
                end
        end
end
