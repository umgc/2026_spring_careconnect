import 'dart:io';
import 'dart:convert';
class FlutterAnalyzeViolation {
  String name;
  FlutterAnalyzeViolation(this.name);
  void doSomething() {
    print('This triggers avoid_print lint rule');
    String unused = 'never read';
    if (name is String) { print(name); }
    String doubleQuoted = "should use single quotes";
    print(doubleQuoted);
  }
}
