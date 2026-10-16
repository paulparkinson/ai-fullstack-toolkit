# Source this file after setting SCRIPT_DIR. Values are treated literally so
# JDBC URLs containing '&' are safe; shell expansion is intentionally disabled.
if [[ -f "$SCRIPT_DIR/.env" ]]; then
  while IFS= read -r env_line || [[ -n "$env_line" ]]; do
    [[ -z "$env_line" || "$env_line" == \#* || "$env_line" != *=* ]] && continue
    env_key="${env_line%%=*}"
    env_value="${env_line#*=}"
    env_key="${env_key//[[:space:]]/}"
    if [[ "$env_key" == "CONTROL_PLANE_API_KEY" ]]; then
      echo "Keep CONTROL_PLANE_API_KEY in the process environment; do not store it in chatgpt/.env." >&2
      continue
    fi
    if [[ ! "$env_key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
      echo "Invalid variable name in chatgpt/.env" >&2
      return 1
    fi
    if (( ${#env_value} >= 2 )); then
      env_first="${env_value:0:1}"
      env_last="${env_value: -1}"
      if [[ ( "$env_first" == '"' && "$env_last" == '"' ) || ( "$env_first" == "'" && "$env_last" == "'" ) ]]; then
        env_value="${env_value:1:${#env_value}-2}"
      fi
    fi
    export "$env_key=$env_value"
  done < "$SCRIPT_DIR/.env"
fi
