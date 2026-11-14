package l.circuit_redstone.logic.cas.logic;

/**
 * 逻辑门类型及其布尔函数。NOT 门只使用第一个输入，第二个输入被忽略。
 */
public enum GateKind {
	AND("and") {
		@Override
		public boolean apply(boolean a, boolean b) {
			return a && b;
		}
	},
	OR("or") {
		@Override
		public boolean apply(boolean a, boolean b) {
			return a || b;
		}
	},
	NOT("not") {
		@Override
		public boolean apply(boolean a, boolean b) {
			return !a;
		}
	},
	XOR("xor") {
		@Override
		public boolean apply(boolean a, boolean b) {
			return a ^ b;
		}
	};

	private final String id;

	GateKind(String id) {
		this.id = id;
	}

	/** 注册用后缀，例如 "and" → and_gate。 */
	public String id() {
		return id;
	}

	/** 门的布尔函数。 */
	public abstract boolean apply(boolean a, boolean b);

	/** 该门是否需要读取第二个输入（左右两侧）。 */
	public boolean usesSecondaryInput() {
		return this != NOT;
	}
}
