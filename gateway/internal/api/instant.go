package api

import (
	"bytes"
	"encoding/json"
	"errors"
	"math/big"
	"time"
)

// Spring/Jackson accepts both ISO strings and numeric epoch seconds. The bundled
// Java HTTP client uses numeric Instants by default; avoid float64 precision loss.
func parseInstant(raw json.RawMessage) (time.Time, error) {
	invalid := errors.New("invalid timestamp")
	raw = bytes.TrimSpace(raw)
	if len(raw) == 0 || bytes.Equal(raw, []byte("null")) { return time.Time{}, invalid }
	if raw[0] == '"' {
		var value string
		if json.Unmarshal(raw, &value) != nil { return time.Time{}, invalid }
		return time.Parse(time.RFC3339Nano, value)
	}
	var number json.Number
	if json.Unmarshal(raw, &number) != nil { return time.Time{}, invalid }
	// Jackson's default serializer emits plain decimals. Bound length and reject
	// exponents before big.Rat parsing to prevent attacker-controlled huge powers.
	if len(raw) > 32 || bytes.ContainsAny(raw, "eE") { return time.Time{}, invalid }
	seconds, ok := new(big.Rat).SetString(number.String())
	if !ok { return time.Time{}, invalid }
	// Limit to the common ISO year range before scaling. This also bounds resource use.
	if seconds.Cmp(big.NewRat(-62167219200, 1)) < 0 || seconds.Cmp(big.NewRat(253402300800, 1)) >= 0 {
		return time.Time{}, invalid
	}
	scaled := new(big.Int).Mul(seconds.Num(), big.NewInt(1000000000))
	scaled.Quo(scaled, seconds.Denom())
	nanos := new(big.Int)
	whole, _ := new(big.Int).QuoRem(scaled, big.NewInt(1000000000), nanos)
	// Match Jackson DecimalUtils.extractSecondsAndNanos(..., true): its legacy
	// signed-seconds encoding uses positive fractional nanos when seconds < 0.
	// For -0.x the whole seconds are zero and the fraction remains negative.
	if whole.Sign() < 0 { nanos.Abs(nanos) }
	return time.Unix(whole.Int64(), nanos.Int64()).UTC(), nil
}
