Unicode true
SetCompressor /SOLID lzma
RequestExecutionLevel user

!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "WinMessages.nsh"

!addplugindir /amd64-unicode "${__FILEDIR__}/Plugins/amd64-unicode"
!addplugindir /x86-unicode "${__FILEDIR__}/Plugins/x86-unicode"
!addplugindir /x86-ansi "${__FILEDIR__}/Plugins/x86-ansi"

!ifndef APP_BINARY
  !error "APP_BINARY define is required (path to maverick-ssh-mcp.exe)."
!endif

!ifndef APP_EXE_NAME
  !define APP_EXE_NAME "maverick-ssh-mcp.exe"
!endif

!ifndef APP_VERSION
  !define APP_VERSION "unknown"
!endif

Name "Maverick SSH MCP"
InstallDir "$LOCALAPPDATA\Programs\Jadaptive\Maverick SSH MCP"
InstallDirRegKey HKCU "Software\Jadaptive\MaverickSSHMCP" "InstallDir"

!define MUI_ABORTWARNING
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"

Section "Install"
  SetShellVarContext current
  SetOutPath "$INSTDIR"
  File /oname=${APP_EXE_NAME} "${APP_BINARY}"
  WriteUninstaller "$INSTDIR\Uninstall.exe"

  CreateDirectory "$SMPROGRAMS\Jadaptive"
  CreateShortcut "$SMPROGRAMS\Jadaptive\Maverick SSH MCP.lnk" "$INSTDIR\${APP_EXE_NAME}"
  CreateShortcut "$SMPROGRAMS\Jadaptive\Uninstall Maverick SSH MCP.lnk" "$INSTDIR\Uninstall.exe"

  WriteRegStr HKCU "Software\Jadaptive\MaverickSSHMCP" "InstallDir" "$INSTDIR"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MaverickSSHMCP" "DisplayName" "Maverick SSH MCP"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MaverickSSHMCP" "DisplayVersion" "${APP_VERSION}"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MaverickSSHMCP" "Publisher" "Jadaptive"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MaverickSSHMCP" "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MaverickSSHMCP" "UninstallString" "$\"$INSTDIR\Uninstall.exe$\""
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MaverickSSHMCP" "NoModify" 1
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MaverickSSHMCP" "NoRepair" 1

  EnVar::SetHKCU
  EnVar::AddValue "Path" "$INSTDIR"
  Pop $0
  System::Call 'USER32::SendMessageTimeout(p 0xffff, i ${WM_SETTINGCHANGE}, p 0, t "Environment", i 0, i 5000, *p .r9)'
SectionEnd

Section "Uninstall"
  SetShellVarContext current

  EnVar::SetHKCU
  EnVar::DeleteValue "Path" "$INSTDIR"
  Pop $0
  System::Call 'USER32::SendMessageTimeout(p 0xffff, i ${WM_SETTINGCHANGE}, p 0, t "Environment", i 0, i 5000, *p .r9)'

  Delete "$SMPROGRAMS\Jadaptive\Maverick SSH MCP.lnk"
  Delete "$SMPROGRAMS\Jadaptive\Uninstall Maverick SSH MCP.lnk"
  RMDir "$SMPROGRAMS\Jadaptive"

  Delete "$INSTDIR\${APP_EXE_NAME}"
  Delete "$INSTDIR\Uninstall.exe"
  RMDir "$INSTDIR"

  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MaverickSSHMCP"
  DeleteRegKey HKCU "Software\Jadaptive\MaverickSSHMCP"
SectionEnd
